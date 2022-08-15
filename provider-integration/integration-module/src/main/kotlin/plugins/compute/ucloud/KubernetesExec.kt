package dk.sdu.cloud.plugins.compute.ucloud

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select

class ExecContext(
    val outputs: ReceiveChannel<ExecMessage>,
    val stdin: SendChannel<ByteArray>,
    val resizes: SendChannel<ExecResizeMessage>,
)

@OptIn(ExperimentalCoroutinesApi::class, io.ktor.util.InternalAPI::class)
suspend fun KubernetesClient.exec(
    resource: KubernetesResourceLocator,
    command: List<String>,
    stdin: Boolean = true,
    tty: Boolean = true,
    stderr: Boolean = true,
    stdout: Boolean = true,
    block: suspend ExecContext.() -> Unit,
) {
    val k8Client = this
    val webSocketClient = HttpClient(CIO) {
        install(WebSockets)
        expectSuccess = false
        engine {
            https {
                trustManager = k8Client.conn.trustManager
            }
        }
    }

    webSocketClient.webSocket(
        request = {
            this.method = HttpMethod.Get
            val buildUrl = buildUrl(
                resource,
                mapOf(
                    "stdin" to stdin.toString(),
                    "tty" to tty.toString(),
                    "stdout" to stdout.toString(),
                    "stderr" to stderr.toString(),
                ),
                "exec"
            )
            url(
                buildUrl.also { println("Connecting to $it") }
            )
            configureRequest(this)
            url(url.fixedClone().let {
                URLBuilder(it).apply {
                    parameters.clear()

                    it.parameters.entries().forEach { (k, values) ->
                        parameters.appendAll(k, values)
                    }
                    command.forEach { parameters.append("command", it) }

                    protocol = if (buildUrl.startsWith("https://")) URLProtocol.WSS else URLProtocol.WS
                }
            }.toString().also { println("After fix: $it") })
        },

        block = {
            coroutineScope {
                val resizeChannel = Channel<ExecResizeMessage>()
                val ingoingChannel = Channel<ExecMessage>()
                val outgoingChannel = Channel<ByteArray>()

                val resizeJob = launch {
                    while (isActive) {
                        // NOTE(Dan): I have no clue where this is documented.
                        // I found this through a combination of Wireshark and this comment:
                        // https://github.com/fabric8io/kubernetes-client/issues/1374#issuecomment-492884783
                        val nextMessage = resizeChannel.receiveCatching().getOrNull() ?: break
                        outgoing.send(
                            Frame.Binary(
                                true,
                                byteArrayOf(4) +
                                    """{"Width": ${nextMessage.cols}, "Height": ${nextMessage.rows}}"""
                                        .toByteArray(Charsets.UTF_8)
                            )
                        )
                    }
                }

                val outgoingJob = launch {
                    while (isActive) {
                        val nextMessage = outgoingChannel.receiveCatching().getOrNull() ?: break
                        outgoing.send(Frame.Binary(true, byteArrayOf(0) + nextMessage))
                    }
                }

                val ingoingJob = launch {
                    while (isActive) {
                        val f = incoming.receiveCatching().getOrNull() ?: break
                        if (f !is Frame.Binary) continue
                        val stream = ExecStream.allValues.getOrNull(f.buffer.get().toInt()) ?: continue
                        val array = ByteArray(f.buffer.remaining())
                        f.buffer.get(array)
                        ingoingChannel.send(ExecMessage(stream, array))
                    }
                }

                val userJob = launch {
                    ExecContext(ingoingChannel, outgoingChannel, resizeChannel).block()
                }

                select<Unit> {
                    resizeJob.onJoin { runCatching { cancel() } }
                    userJob.onJoin { runCatching { cancel() } }
                    outgoingJob.onJoin { runCatching { cancel() } }
                    ingoingJob.onJoin { runCatching { cancel() } }
                }
            }
        }
    )
}

enum class ExecStream(val id: Int) {
    STDOUT(1),
    STDERR(2),
    INFO(3);

    companion object {
        val allValues = arrayOf<ExecStream?>(null, STDOUT, STDERR, INFO)
    }
}

data class ExecMessage(val stream: ExecStream, val bytes: ByteArray)

data class ExecResizeMessage(val cols: Int, val rows: Int)
