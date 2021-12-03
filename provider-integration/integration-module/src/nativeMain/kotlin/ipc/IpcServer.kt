package dk.sdu.cloud.ipc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import platform.linux.sockaddr_un
import platform.posix.*

data class IpcUser(val uid: UInt, val gid: UInt, val pid: Int)

data class TypedIpcHandler<Req, Resp>(
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Resp>,
    val handler: IpcHandler,
)

abstract class IpcContainer(val namespace: String) {
    val handlers = ArrayList<IpcHandler>()

    inline fun <reified Request, reified Response> ipcHandler(
        method: String,
        noinline handler: (user: IpcUser, request: Request) -> Response
    ): TypedIpcHandler<Request, Response> {
        val handler = IpcHandler(method) { user, request ->
            val mappedRequest = runCatching {
                defaultMapper.decodeFromJsonElement<Request>(request.params)
            }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

            defaultMapper.encodeToJsonElement(
                handler(user, mappedRequest)
            ) as JsonObject
        }

        handlers.add(handler)
        return TypedIpcHandler(serializer(), serializer(), handler)
    }

    inline fun <reified Request, reified Response> createHandler(
        noinline handler: (user: IpcUser, request: Request) -> Response,
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete", handler)
    }

    inline fun <reified Request, reified Response> deleteHandler(
        noinline handler: (user: IpcUser, request: Request) -> Response,
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.delete", handler)
    }

    inline fun <reified Request, reified Response> browseHandler(
        noinline handler: (user: IpcUser, request: Request) -> Response,
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.browse", handler)
    }

    inline fun <reified Request, reified Response> updateHandler(
        operation: String,
        noinline handler: (user: IpcUser, request: Request) -> Response,
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.$operation", handler)
    }

    inline fun <reified Request, reified Response> retrieveHandler(
        noinline handler: (user: IpcUser, request: Request) -> Response,
    ): TypedIpcHandler<Request, Response> {
        return ipcHandler("$namespace.retrieve", handler)
    }
}


data class IpcHandler(
    val method: String,
    val methodIsPrefix: Boolean = false,
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

class IpcServer(
    private val ipcSocketDirectory: String,
) {
    private val ipcHandlers = ArrayList<IpcHandler>()
    val handlers: List<IpcHandler>
        get() = ipcHandlers

    fun runServer(): Unit = memScoped {
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
                processIpcClient(clientSocket)
            }
        }
    }

    fun addHandler(handler: IpcHandler) {
        ipcHandlers.add(handler)
    }

    private fun processIpcClient(clientSocket: Int) = memScoped {
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
                else IpcUser(uid, gid, pid)
            }
        } ?: return

        while (true) {
            val request = runCatching { parseRequest() }.getOrNull() ?: break
            if (request.id == null) continue

            val response: Result<JsonObject> = runCatching {
                for (handler in handlers) {
                    if (!handler.matches(request.method)) continue
                    return@runCatching handler.handler(user, request)
                }

                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            val unwrappedResponse: JsonRpcResponse = if (response.isFailure) {
                val asRpcException = (response.exceptionOrNull() as? RPCException)
                if (asRpcException != null) {
                    JsonRpcResponse.Error(
                        JsonRpcResponse.ErrorObject(
                            asRpcException.httpStatusCode.value,
                            asRpcException.why
                        ),
                        request.id
                    )
                } else {
                    log.warn(response.exceptionOrNull()!!.stackTraceToString())
                    JsonRpcResponse.Error(
                        JsonRpcResponse.ErrorObject(500, "Internal error"),
                        request.id
                    )
                }
            } else {
                val result = response.getOrThrow()
                JsonRpcResponse.Success(result, request.id)
            }

            val serializer: KSerializer<*> = when (unwrappedResponse) {
                is JsonRpcResponse.Error -> JsonRpcResponse.Error.serializer()
                is JsonRpcResponse.Success -> JsonRpcResponse.Success.serializer()
            }

            runCatching {
                @Suppress("UNCHECKED_CAST")
                writePipe.sendFully(
                    clientSocket,
                    (defaultMapper.encodeToString(serializer as KSerializer<Any>, unwrappedResponse) + "\n").encodeToByteArray()
                )
            }.getOrNull() ?: break
        }

        if (clientSocket >= 0) close(clientSocket)
    }


    companion object : Loggable {
        override val log = logger()

        const val SCM_CREDENTIALS = 2
    }
}

class IpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

const val ipcSocketName = "ucloud.sock"
