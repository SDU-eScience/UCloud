package dk.sdu.cloud.service.k8

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val webSocketClient = HttpClient(CIO).config {
    install(io.ktor.client.features.websocket.WebSockets)
}

class ExecContext(
    val outputs: ReceiveChannel<ExecMessage>,
    val stdin: SendChannel<ByteArray>
)

suspend fun KubernetesClient.exec(
    resource: KubernetesResourceLocator,
    command: List<String>,
    stdin: Boolean = true,
    tty: Boolean = true,
    stderr: Boolean = true,
    stdout: Boolean = true,
    block: suspend ExecContext.() -> Unit,
) {
    webSocketClient.webSocket(
        request = {
            this.method = HttpMethod.Get
            url(
                buildUrl(
                    resource,
                    mapOf(
                        "stdin" to stdin.toString(),
                        "tty" to tty.toString(),
                        "stdout" to stdout.toString(),
                        "stderr" to stderr.toString(),
                        //"command" to command.joinToString(" ") { "\"$it\"" }
                    ),
                    "exec"
                )
            )
            configureRequest(this)
            url(url.fixedClone().let {
                it.copy(
                    parameters = Parameters.build {
                        it.parameters.entries().forEach { (k, values) ->
                            appendAll(k, values)
                        }
                        command.forEach { append("command", it) }
                    },
                    protocol = URLProtocol.WS
                )
            }.toString())
        },

        block = {
            coroutineScope {
                val ingoingChannel = Channel<ExecMessage>()
                val outgoingChannel = Channel<ByteArray>()
                launch {
                    while (isActive) {
                        val nextMessage = outgoingChannel.receiveOrNull() ?: break
                        outgoing.send(Frame.Binary(true, byteArrayOf(0) + nextMessage))
                    }
                }

                launch {
                    while (isActive) {
                        val f = incoming.receiveOrNull() ?: break
                        if (f !is Frame.Binary) continue
                        val stream = ExecStream.allValues.getOrNull(f.buffer.get().toInt()) ?: continue
                        val array = ByteArray(f.buffer.remaining())
                        f.buffer.get(array)
                        ingoingChannel.send(ExecMessage(stream, array))
                    }
                }

                ExecContext(ingoingChannel, outgoingChannel).block()
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