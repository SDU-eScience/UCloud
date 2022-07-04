package dk.sdu.cloud.ipc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.executeCommandToText
import jdk.net.ExtendedSocketOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonObject
import libc.clib
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

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
            defaultMapper.decodeFromJsonElement(requestSerializer, request.params)
        }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

        defaultMapper.encodeToJsonElement(
            responseSerializer,
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
    val proxy = call("proxy", IpcToIntegrationModuleRequest.serializer(), JsonObject.serializer(), CommonErrorMessage.serializer()) {
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
        val socket = File("$ipcSocketDirectory/$ipcSocketName")
        if (socket.exists()) socket.delete()

        val channel = ServerSocketChannel
            .open(StandardProtocolFamily.UNIX)
            .bind(UnixDomainSocketAddress.of(socket.toPath()))

        clib.chmod(socket.absolutePath, "777".toInt(8))

        while (true) {
            val client = channel.accept()
            ProcessingScope.launch {
                try {
                    processIpcClient(client)
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

    private suspend fun processIpcClient(client: SocketChannel) {
        val writeBuffer = ByteBuffer.allocate(1024 * 4)
        val readBuffer = ByteBuffer.allocate(1024 * 4)
        val messageBuilder = MessageBuilder(1024 * 16)

        val user = run {
            try {
                // NOTE(Dan): really annoyingly, the java API is fully aware of the UID and even saves it. It is just not
                // accessible, not even through reflection.
                val principal = client.getOption(ExtendedSocketOptions.SO_PEERCRED)

                val uid = executeCommandToText("/usr/bin/id") {
                    addArg("-u")
                    addArg(principal.user.name)
                }.stdout.trim().toInt()

                val gid = executeCommandToText("/usr/bin/id") {
                    addArg("-g")
                    addArg(principal.group.name)
                }.stdout.trim().toInt()

                IpcUser(uid, gid)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                throw ex
            }
        }

        fun parseRequest(): JsonRpcRequest {
            val decodedText = messageBuilder.readNextMessage(client, readBuffer)
            return defaultMapper.decodeFromString(JsonRpcRequest.serializer(), decodedText)
        }

        try {
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

                    while (writeBuffer.hasRemaining()) client.write(writeBuffer)
                }.getOrNull() ?: break
            }
        } finally {
            client.close()
        }
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
    }
}
