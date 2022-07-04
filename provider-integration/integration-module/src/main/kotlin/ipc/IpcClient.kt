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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
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

inline fun <reified R> JsonRpcResponse.orThrow(): R {
    return when (this) {
        is JsonRpcResponse.Success -> {
            defaultMapper.decodeFromJsonElement(serializer<R>(), result)
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

            fun sendRequest(request: JsonRpcRequest) {
                writeBuffer.clear()
                val encoded = (defaultMapper.encodeToString(request) + "\n").encodeToByteArray()
                writeBuffer.put(encoded)
                writeBuffer.flip()
                while (writeBuffer.hasRemaining()) channel.write(writeBuffer)
            }

            val messageBuilder = MessageBuilder(1024 * 1024)

            fun parseResponse(): JsonRpcResponse {
                val decodedText = messageBuilder.readNextMessage(channel, readBuffer)
                val decodedJson = defaultMapper.decodeFromString<JsonObject>(decodedText)

                return when {
                    decodedJson.containsKey("result") -> {
                        defaultMapper.decodeFromJsonElement<JsonRpcResponse.Success>(decodedJson)
                    }
                    decodedJson.containsKey("error") -> {
                        defaultMapper.decodeFromJsonElement<JsonRpcResponse.Error>(decodedJson)
                    }
                    else -> {
                        throw IpcException("Invalid response")
                    }
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

suspend inline fun <reified Req, reified Resp> IpcClient.sendRequest(
    call: TypedIpcHandler<Req, Resp>,
    request: Req
): Resp {
    return sendRequest(
        JsonRpcRequest(
            call.method,
            defaultMapper.encodeToJsonElement(request) as JsonObject
        )
    ).orThrow()
}

inline fun <reified Req, reified Resp> IpcClient.sendRequestBlocking(
    call: TypedIpcHandler<Req, Resp>,
    request: Req
): Resp {
    return runBlocking {
        sendRequest(
            JsonRpcRequest(
                call.method,
                defaultMapper.encodeToJsonElement(request) as JsonObject
            )
        ).orThrow()
    }
}
