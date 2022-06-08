package dk.sdu.cloud.ipc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.config.*
import dk.sdu.cloud.http.HttpContext
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.service.Loggable
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import platform.linux.sockaddr_un
import platform.posix.*

data class IpcUser(val uid: UInt, val gid: UInt)

data class TypedIpcHandler<Req, Resp>(
    val method: String,
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Resp>,
)

inline fun <reified Req, reified Resp> TypedIpcHandler<Req, Resp>.handler(
    noinline handler: (user: IpcUser, request: Req) -> Resp
): IpcHandler {
    return IpcHandler(method) { user, request ->
        val mappedRequest = runCatching {
            defaultMapper.decodeFromJsonElement<Req>(request.params)
        }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

        defaultMapper.encodeToJsonElement(
            handler(user, mappedRequest)
        ) as JsonObject
    }
}
inline fun <reified Req, reified Resp> TypedIpcHandler<Req, Resp>.suspendingHandler(
    noinline block: suspend (user: IpcUser, request: Req) -> Resp
): IpcHandler {
    return handler { user, request ->
        runBlocking {
            block(user, request)
        }
    }
}

fun IpcHandler.register(server: IpcServer) {
    server.addHandler(this)
}

abstract class IpcContainer(val namespace: String) {
    inline fun <reified Request, reified Response> ipcHandler(method: String): TypedIpcHandler<Request, Response> {
        return TypedIpcHandler(method, serializer(), serializer())
    }

    inline fun <reified Request, reified Response> createHandler(): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete")
    }

    inline fun <reified Request, reified Response> deleteHandler(): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete")
    }

    inline fun <reified Request, reified Response> browseHandler(): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.browse")
    }

    inline fun <reified Request, reified Response> updateHandler(operation: String): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.$operation")
    }

    inline fun <reified Request, reified Response> retrieveHandler(): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.retrieve")
    }
}


data class IpcHandler(
    val method: String,
    val methodIsPrefix: Boolean = false,
    var allowProxyToRemoteIntegrationModule: Boolean = false,
    val handler: (user: IpcUser, request: JsonRpcRequest) -> JsonObject
) {
    fun matches(method: String): Boolean {
        return if (methodIsPrefix) {
            method.startsWith(this.method)
        } else {
            method == this.method
        }
    }
}

@Serializable
data class IpcToIntegrationModuleRequest(
    val uid: UInt,
    val gid: UInt,
    val request: JsonRpcRequest
)

private object IpcToIntegrationModuleApi : CallDescriptionContainer("ipcproxy") {
    val proxy = call<IpcToIntegrationModuleRequest, JsonObject, CommonErrorMessage>("proxy") {
        httpUpdate("/ipc-proxy", "proxy", Roles.PUBLIC)
    }
}

class IpcServer(
    private val ipcSocketDirectory: String,
    private val frontendProxyConfig: VerifiedConfig.FrontendProxy?,
    private val rpcClient: AuthenticatedClient,
    private val rpcServer: RpcServer?,
) {
    private val isProxy = rpcServer == null
    private val ipcHandlers = ArrayList<IpcHandler>()
    val closeClientIds = ArrayList<UInt>()
    val handlers: List<IpcHandler>
        get() = ipcHandlers

    fun runServer(): Unit = memScoped {
        registerRpcHandler()
        val socketPath = "$ipcSocketDirectory/$ipcSocketName"
        unlink(socketPath)
        val serverSocket = socket(AF_UNIX, SOCK_STREAM, 0)
        if (serverSocket == -1) throw IpcException("socket error")

        val address = UnixSockets.buildAddress(this, socketPath)

        if (bind(serverSocket, address.ptr.reinterpret(), sizeOf<sockaddr_un>().toUInt()) == -1) {
            throw IpcException("Could not bind IPC socket")
        }

        if (listen(serverSocket, 32) == -1) throw IpcException("Failed to listen on IPC socket")

        // NOTE(Dan): Permissions are enforced by SO_PEERCRED. As a result, we can let anybody connect to it without
        // problems.
        chmod(socketPath, "777".toUInt(8))

        while (true) {
            val clientSocket = accept(serverSocket, null, null)

            ProcessingScope.launch {
                try {
                    processIpcClient(clientSocket)
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    throw ex
                }
            }
        }
    }

    fun requestClientRestart(uid: UInt) {
        closeClientIds.add(uid)
    }

    fun clientShouldRestart(uid: UInt): Boolean  {
        return closeClientIds.contains(uid)
    }

    fun addHandler(handler: IpcHandler) {
        ipcHandlers.add(handler)
    }

    private fun registerRpcHandler() {
        // If the rpcServer is null, then we are in the frontend proxy, and we don't need to do anything
        if (rpcServer == null) return
        if (frontendProxyConfig == null) return

        rpcServer.implement(IpcToIntegrationModuleApi.proxy) {
            val sctx = (ctx.serverContext as? HttpContext) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val bearerToken = sctx.headers.find { it.header.equals("Authorization", ignoreCase = true) }
                ?.value?.removePrefix("Bearer ")
            if (bearerToken != frontendProxyConfig.sharedSecret) {
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }

            for (ipcHandler in handlers) {
                if (ipcHandler.matches(request.request.method) && ipcHandler.allowProxyToRemoteIntegrationModule) {
                    val response = ipcHandler.handler(IpcUser(request.uid, request.gid), request.request)
                    return@implement OutgoingCallResponse.Ok(response)
                }
            }
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    private suspend fun processIpcClient(clientSocket: Int) = memScoped {
        val log = Log("IpcServer")

        val readPipe = UnixSocketPipe.create(this, 1024 * 32, 0)
        val writePipe = UnixSocketPipe.create(this, 1024 * 32, 0)

        val messageBuilder = MessageBuilder(1024 * 1024)
        fun parseRequest(): JsonRpcRequest {
            val decodedText = messageBuilder.readNextMessage(clientSocket, readPipe)
            return defaultMapper.decodeFromString<JsonRpcRequest>(decodedText)
        }

        val user = run {
            val initialRequest = parseRequest()
            if (initialRequest.id != null) return@run null

            val credentials = getSocketCredentials(clientSocket, readPipe.messageHeader.ptr)
            credentials.useContents {
                if (!valid) null
                else IpcUser(uid, gid)
            }
        } ?: return

        while (true) {
            val request = runCatching { parseRequest() }.getOrNull() ?: break
            if (request.id == null) continue

            val response = handleRequest(user, request)

            val serializer: KSerializer<*> = when (response) {
                is JsonRpcResponse.Error -> JsonRpcResponse.Error.serializer()
                is JsonRpcResponse.Success -> JsonRpcResponse.Success.serializer()
            }

            runCatching {
                @Suppress("UNCHECKED_CAST")
                writePipe.sendFully(
                    clientSocket,
                    (defaultMapper.encodeToString(serializer as KSerializer<Any>, response) + "\n").encodeToByteArray()
                )
            }.getOrNull() ?: break
        }

        if (clientSocket >= 0) close(clientSocket)
    }

    private suspend fun handleRequest(user: IpcUser, request: JsonRpcRequest): JsonRpcResponse {
        return if (!isProxy) handleLocalRequest(user, request)
        else handleProxyRequest(user, request)
    }

    private fun handleLocalRequest(user: IpcUser, request: JsonRpcRequest): JsonRpcResponse {
        check(!isProxy)
        val id = request.id ?: error("request.id is null")

        val response: Result<JsonObject> = runCatching {
            for (handler in handlers) {
                if (!handler.matches(request.method)) continue
                return@runCatching handler.handler(user, request)
            }

            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        return if (response.isFailure) {
            val asRpcException = (response.exceptionOrNull() as? RPCException)
            if (asRpcException != null) {
                JsonRpcResponse.Error(
                    JsonRpcResponse.ErrorObject(
                        asRpcException.httpStatusCode.value,
                        asRpcException.why
                    ),
                    id
                )
            } else {
                log.warn(response.exceptionOrNull()!!.stackTraceToString())
                JsonRpcResponse.Error(
                    JsonRpcResponse.ErrorObject(500, "Internal error"),
                    id
                )
            }
        } else {
            val result = response.getOrThrow()
            JsonRpcResponse.Success(result, id)
        }
    }

    private suspend fun handleProxyRequest(user: IpcUser, request: JsonRpcRequest): JsonRpcResponse {
        check(isProxy)
        val id = request.id ?: error("request.id is null")

        val response = IpcToIntegrationModuleApi.proxy.call(
            IpcToIntegrationModuleRequest(user.uid, user.gid, request),
            rpcClient
        )

        return when (response) {
            is IngoingCallResponse.Ok -> {
                JsonRpcResponse.Success(response.result, id)
            }
            is IngoingCallResponse.Error -> {
                JsonRpcResponse.Error(
                    JsonRpcResponse.ErrorObject(response.statusCode.value, response.error?.why ?: "Error"),
                    id
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val SCM_CREDENTIALS = 2
    }
}

class IpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

const val ipcSocketName = "ucloud.sock"
