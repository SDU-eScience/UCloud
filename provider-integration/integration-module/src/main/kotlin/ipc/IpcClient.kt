package dk.sdu.cloud.ipc

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.random.nextULong

@Serializable
data class JsonRpcRequest(
    val method: String,
    val params: JsonObject,
    val id: String? = Random.nextULong().toString(16),
    val jsonrpc: String = "2.0"
)

fun JsonRpcNotification(
    method: String,
    params: JsonObject,
): JsonRpcRequest {
    return JsonRpcRequest(method, params, null)
}

sealed class JsonRpcResponse {
    abstract val id: String
    abstract val jsonrpc: String

    @Serializable
    data class Success(
        val result: JsonObject,
        override val id: String,
        override val jsonrpc: String = "2.0"
    ) : JsonRpcResponse()

    @Serializable
    data class Error(
        val error: ErrorObject,
        override val id: String,
        override val jsonrpc: String = "2.0"
    ) : JsonRpcResponse() {
        fun rethrow(): Nothing {
            throw RPCException(error.message, HttpStatusCode.parse(error.code))
        }
    }

    @Serializable
    data class ErrorObject(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    )
}

fun <R> JsonRpcResponse.orThrow(serializer: KSerializer<R>): R {
    return when (this) {
        is JsonRpcResponse.Success -> {
            defaultMapper.decodeFromJsonElement(serializer, result)
        }

        is JsonRpcResponse.Error -> {
            rethrow()
        }
    }
}

data class IpcMessage(val payload: JsonRpcRequest, val callback: SendChannel<Result<JsonRpcResponse>>)

class IpcClient(
    private val ipcDirectory: String,
) {
    private val writeChannel = Channel<IpcMessage>(capacity = Channel.UNLIMITED)
    private val isProcessing = AtomicBoolean(false)

    fun connect() {
        if (!isProcessing.compareAndSet(false, true)) {
            throw IpcException("We are already processing messages")
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        ProcessingScope.launch {
            val socket = File("$ipcDirectory/$ipcSocketName")
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)

            if (!channel.connect(UnixDomainSocketAddress.of(socket.toPath()))) {
                while (true) {
                    if (channel.finishConnect()) break
                }
            }

            val writeBuffer = ByteBuffer.allocate(1024 * 64)
            val readBuffer = ByteBuffer.allocate(1024 * 64)

            // NOTE(Dan): The IPC system uses line delimited (\n) JSON-RPC 2.0
            // NOTE(Dan): We don't currently support batch requests

            fun sendMessage(message: String) {
                writeBuffer.clear()
                val encoded = message.encodeToByteArray()
                writeBuffer.put(encoded)
                writeBuffer.flip()
                while (writeBuffer.hasRemaining()) channel.write(writeBuffer)
            }

            fun sendRequest(request: JsonRpcRequest) {
                sendMessage(defaultMapper.encodeToString(JsonRpcRequest.serializer(), request) + "\n")
            }

            val messageBuilder = MessageBuilder(1024 * 1024)

            fun parseResponse(): JsonRpcResponse {
                val decodedText = messageBuilder.readNextMessage(channel, readBuffer)
                val decodedJson = defaultMapper.decodeFromString(JsonObject.serializer(), decodedText)

                return when {
                    decodedJson.containsKey("result") -> {
                        defaultMapper.decodeFromJsonElement(JsonRpcResponse.Success.serializer(), decodedJson)
                    }
                    decodedJson.containsKey("error") -> {
                        defaultMapper.decodeFromJsonElement(JsonRpcResponse.Error.serializer(), decodedJson)
                    }
                    else -> {
                        throw IpcException("Invalid response")
                    }
                }
            }

            val authMessage = messageBuilder.readNextMessage(channel, readBuffer)
            when {
                authMessage == "WELCOME" -> {
                    // All good, SO_PEERCRED did its job
                }

                authMessage.startsWith("AUTH ") -> {
                    // Fallback authentication mechanism
                    val messageSplit = authMessage.split(" ")
                    if (messageSplit.size != 2) {
                        throw IpcException("Invalid auth message received from server. Bailing out: $authMessage")
                    }

                    val token = messageSplit[1]
                    val authDir = File(ipcDirectory, "auth")
                    File(authDir, token).writeText(token)

                    sendMessage("OK")
                }

                else -> {
                    // Corrupt state or bad server implementation. Bail out.
                    throw IpcException("Invalid auth message received from server. Bailing out: $authMessage")
                }
            }

            sendRequest(JsonRpcNotification("ping", JsonObject(emptyMap())))

            while (!writeChannel.isClosedForReceive) {
                val next = writeChannel.receiveCatching().getOrNull() ?: break

                try {
                    sendRequest(next.payload)
                    val value = parseResponse()
                    runBlocking { next.callback.send(Result.success(value)) }
                } catch (ex: Throwable) {
                    log.warn("Caught exception while processing IPC messages (client): ${ex.stackTraceToString()}")
                    channel.close()
                    runBlocking { next.callback.send(Result.failure(ex)) }
                }
            }
        }
    }

    suspend fun sendRequest(request: JsonRpcRequest): JsonRpcResponse {
        val channel = Channel<Result<JsonRpcResponse>>(1)
        writeChannel.send(IpcMessage(request, channel))
        return channel.receive().getOrThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

inline fun IpcClient.sendRequestBlocking(request: JsonRpcRequest): JsonRpcResponse {
    return runBlocking { sendRequest(request) }
}

suspend fun <Req, Resp> IpcClient.sendRequest(
    call: TypedIpcHandler<Req, Resp>,
    request: Req
): Resp {
    return sendRequest(
        JsonRpcRequest(
            call.method,
            defaultMapper.encodeToJsonElement(call.requestSerializer, request) as JsonObject
        )
    ).orThrow(call.responseSerializer)
}

fun <Req, Resp> IpcClient.sendRequestBlocking(
    call: TypedIpcHandler<Req, Resp>,
    request: Req
): Resp {
    return runBlocking {
        sendRequest(
            JsonRpcRequest(
                call.method,
                defaultMapper.encodeToJsonElement(call.requestSerializer, request) as JsonObject
            )
        ).orThrow(call.responseSerializer)
    }
}
