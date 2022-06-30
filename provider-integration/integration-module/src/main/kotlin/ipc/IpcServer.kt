package dk.sdu.cloud.ipc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import libc.AF_UNIX
import libc.SOCK_STREAM
import libc.clib
import java.io.File
import java.nio.ByteBuffer

data class IpcUser(val uid: Int, val gid: Int)

data class TypedIpcHandler<Req, Resp>(
    val method: String,
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Resp>,
)

inline fun <reified Req, reified Resp> TypedIpcHandler<Req, Resp>.handler(
    noinline handler: suspend (user: IpcUser, request: Req) -> Resp
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
    inline fun <reified Request, reified Response> ipcHandler(
        method: String,
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return TypedIpcHandler(method, request, response)
    }

    inline fun <reified Request, reified Response> createHandler(
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete", request, response)
    }

    inline fun <reified Request, reified Response> deleteHandler(
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete", request, response)
    }

    inline fun <reified Request, reified Response> browseHandler(
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.browse", request, response)
    }

    inline fun <reified Request, reified Response> updateHandler(
        operation: String,
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.$operation", request, response)
    }

    inline fun <reified Request, reified Response> retrieveHandler(
        request: KSerializer<Request>,
        response: KSerializer<Response>
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.retrieve", request, response)
    }
}


data class IpcHandler(
    val method: String,
    val methodIsPrefix: Boolean = false,
    var allowProxyToRemoteIntegrationModule: Boolean = false,
    val handler: suspend (user: IpcUser, request: JsonRpcRequest) -> JsonObject
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
    val uid: Int,
    val gid: Int,
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
    val closeClientIds: MutableSet<Int> = mutableSetOf()
    val handlers: List<IpcHandler>
        get() = ipcHandlers

    fun runServer() {
        registerRpcHandler()
        val socketPath = "$ipcSocketDirectory/$ipcSocketName"
        File(socketPath).delete()
        val serverSocket = clib.socket(AF_UNIX, SOCK_STREAM, 0)
        if (serverSocket == -1) throw IpcException("socket error")

        val address = clib.buildUnixSocketAddress(socketPath)

        if (clib.bind(serverSocket, address, clib.unixDomainSocketSize()) == -1) {
            throw IpcException("Could not bind IPC socket")
        }

        if (clib.listen(serverSocket, 32) == -1) throw IpcException("Failed to listen on IPC socket")

        // NOTE(Dan): Permissions are enforced by SO_PEERCRED. As a result, we can let anybody connect to it without
        // problems.
        clib.chmod(socketPath, "777".toInt(8))

        while (true) {
            val clientSocket = clib.accept(serverSocket)

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

    fun requestClientRestart(uid: Int) {
        closeClientIds.add(uid)
    }

    fun clientShouldRestart(uid: Int): Boolean  {
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
            val bearerToken = ctx.bearer
            if (bearerToken != frontendProxyConfig.sharedSecret) {
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }

            for (ipcHandler in handlers) {
                if (ipcHandler.matches(request.request.method) && ipcHandler.allowProxyToRemoteIntegrationModule) {
                    val response = ipcHandler.handler(IpcUser(request.uid, request.gid), request.request)
                    ok(response)
                    return@implement
                }
            }

            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    private suspend fun processIpcClient(clientSocket: Int) {
        val log = Logger("IpcServer")

        val writeBuffer = ByteBuffer.allocateDirect(1024 * 64)
        val readBuffer = ByteBuffer.allocateDirect(1024 * 64)

        val messageBuilder = MessageBuilder(1024 * 1024)
        fun parseRequest(): JsonRpcRequest {
            val decodedText = messageBuilder.readNextMessage(clientSocket, readBuffer)
            return defaultMapper.decodeFromString<JsonRpcRequest>(decodedText)
        }

        val user = run {
            val uidAndGid = intArrayOf(-1, -1)
            clib.receiveMessage(clientSocket, readBuffer, uidAndGid)
            if (uidAndGid[0] == -1 || uidAndGid[1] == -1) {
                return@run null
            }

            IpcUser(uidAndGid[0], uidAndGid[1])
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
                writeBuffer.clear()
                val encoded = (defaultMapper.encodeToString(serializer as KSerializer<Any>, response) + "\n")
                    .encodeToByteArray()
                writeBuffer.put(encoded)
                writeBuffer.flip()

                @Suppress("UNCHECKED_CAST")
                ipcSendFully (clientSocket, writeBuffer)
            }.getOrNull() ?: break
        }

        if (clientSocket >= 0) clib.close(clientSocket)
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
                return@runCatching runBlocking { handler.handler(user, request) }
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