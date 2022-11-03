package dk.sdu.cloud.ipc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.DebugCoroutineContext
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.plugins.storage.posix.toInt
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.secureToken
import jdk.net.ExtendedSocketOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonObject
import libc.clib
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.Files as NioFiles

const val USE_SO_PEERCRED = false
const val IPC_AUTH_DIR = "auth"

data class IpcUser(val uid: Int)

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
        return ipcHandler("$namespace.create", request, response)
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
    private val handler: suspend (user: IpcUser, request: JsonRpcRequest) -> JsonObject
) {
    fun matches(method: String): Boolean {
        return if (methodIsPrefix) {
            method.startsWith(this.method)
        } else {
            method == this.method
        }
    }

    suspend fun invokeHandler(user: IpcUser, request: JsonRpcRequest): JsonObject {
        val context = DebugContext.create()
        return withContext(DebugCoroutineContext(context)) {
            val shouldDebug = request.method != "ping.ping"
            val start = Time.now()
            if (shouldDebug) {
                debugSystem?.sendMessage(
                    DebugMessage.ServerRequest(
                        context,
                        start,
                        null,
                        MessageImportance.IMPLEMENTATION_DETAIL,
                        request.method,
                        request.params
                    )
                )
            }

            try {
                val result = handler(user, request)
                val end = Time.now()
                if (shouldDebug) {
                    debugSystem?.sendMessage(
                        DebugMessage.ServerResponse(
                            DebugContext.createWithParent(context.id),
                            end,
                            null,
                            MessageImportance.THIS_IS_NORMAL,
                            request.method,
                            result,
                            200,
                            end - start
                        )
                    )
                }

                result
            } catch (ex: Throwable) {
                val end = Time.now()
                val stack =
                    defaultMapper.encodeToJsonElement(ReadableStackTrace.serializer(), ex.toReadableStacktrace())
                if (!shouldDebug) {
                    debugSystem?.sendMessage(
                        DebugMessage.ServerResponse(
                            DebugContext.createWithParent(context.id),
                            end,
                            null,
                            MessageImportance.THIS_IS_WRONG,
                            request.method,
                            stack,
                            500,
                            end - start
                        )
                    )
                }

                throw ex
            }
        }
    }
}

@Serializable
data class IpcToIntegrationModuleRequest(
    val uid: Int,
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

        val authDir = File(ipcSocketDirectory, IPC_AUTH_DIR).also { it.mkdir() }
        clib.chmod(authDir.absolutePath, "733".toInt(8)) // Allow anyone to write to the auth folder but not read it

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
                    val response = ipcHandler.invokeHandler(IpcUser(request.uid), request.request)
                    ok(response)
                    return@implement
                }
            }

            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    private suspend fun processIpcClient(client: SocketChannel) {
        val writeBuffer = ByteBuffer.allocate(1024 * 64)
        val readBuffer = ByteBuffer.allocate(1024 * 64)
        val messageBuilder = MessageBuilder(1024 * 512)

        fun parseMessage(): JsonRpcRequest {
            val decodedText = messageBuilder.readNextMessage(client, readBuffer)
            return defaultMapper.decodeFromString(JsonRpcRequest.serializer(), decodedText)
        }

        fun sendMessage(data: String): Boolean {
            try {
                writeBuffer.clear()
                val encoded = data.encodeToByteArray()
                writeBuffer.put(encoded)
                writeBuffer.flip()

                while (writeBuffer.hasRemaining()) client.write(writeBuffer)
                return true
            } catch (ex: Throwable) {
                return false
            }
        }

        val user: IpcUser = run {
            try {
                if (!USE_SO_PEERCRED) return@run null

                // NOTE(Dan): really annoyingly, the java API is fully aware of the UID and even saves it. It is just not
                // accessible, not even through reflection.
                val principal = client.getOption(ExtendedSocketOptions.SO_PEERCRED)

                val uid = clib.retrieveUserIdFromName(principal.user.name)

                if (uid == -1) {
                    throw IpcException("Invalid user ${principal.user} ${principal.group} $uid")
                }

                sendMessage("WELCOME")

                IpcUser(uid)
            } catch (ex: UnsupportedOperationException) {
                null
            } catch (ex: Throwable) {
                throw ex
            }
        } ?: run {
            // NOTE(Dan): Annoyingly, GraalVM native images don't support SO_PEERCRED (as of 05/07/2022) even on
            // platforms which do support it. I don't currently have time to rewrite this implementation (again) to
            // use JNI. Thus, we are doing this slightly weird form of challenge authentication where the OS is
            // responsible for the proving authenticity part. In essence, we are asking the user to prove who they are
            // by creating a specific file in a public folder. We use the owner, which under linux cannot be changed
            // without root, to determine the uid of the user who created it.
            val token = secureToken(32) + Time.now()
            sendMessage("AUTH $token\n")

            val response = messageBuilder.readNextMessage(client, readBuffer)
            if (response != "OK") throw IpcException("Bad authentication flow")

            val authDir = File(ipcSocketDirectory, IPC_AUTH_DIR)
            val authFile = File(authDir, token).toPath()
            val attributes = NioFiles
                .readAttributes(authFile, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

            if (attributes.permissions().toInt() != "644".toInt(8)) {
                throw IpcException("Bad permissions on file")
            }

            val uid = clib.retrieveUserIdFromName(attributes.owner().name)
            if (uid == -1) throw IpcException("Unknown user: ${attributes.owner()}")

            IpcUser(uid)
        }

        try {
            while (true) {
                val request = try {
                    parseMessage()
                } catch (ex: Throwable) {
                    break
                }
                if (request.id == null) continue
                val response = handleRequest(user, request)

                val serializer: KSerializer<*> = when (response) {
                    is JsonRpcResponse.Error -> JsonRpcResponse.Error.serializer()
                    is JsonRpcResponse.Success -> JsonRpcResponse.Success.serializer()
                }

                val encodeToString = defaultMapper.encodeToString(serializer as KSerializer<Any>, response)
                if (!sendMessage((encodeToString + "\n"))) {
                    break
                }
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
                return@runCatching runBlocking { handler.invokeHandler(user, request) }
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
            IpcToIntegrationModuleRequest(user.uid, request),
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
