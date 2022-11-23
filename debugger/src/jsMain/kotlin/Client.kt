package dk.sdu.cloud.debug

import dk.sdu.cluod.debug.defaultMapper
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.WebSocket

class Client {
    var onOpen: () -> Unit = {}
    var onMessage: (message: ServerToClient) -> Unit = { }
    var onClose: () -> Unit = { }

    private var socket: WebSocket? = null

    fun start() {
        if (socket != null) return

        val socket = WebSocket("wss://${document.location?.host ?: ""}")
        this.socket = socket

        socket.onopen = {
            onOpen()
        }

        socket.onclose = {
            onClose()

            this@Client.socket = null
            window.setTimeout({ start() }, 5000)
        }

        val onMessageReturn = true.asDynamic()
        socket.onmessage = f@{ message ->
            val data = message.data as? String ?: return@f onMessageReturn

            val decodedMessage = try {
                defaultMapper.decodeFromString(ServerToClient.serializer(), data)
            } catch (ex: Throwable) {
                println("Invalid message!" +
                    "\n  Message: ${data.removeSuffix("\n")}" +
                    "\n  ${ex::class.simpleName}: ${ex.message?.prependIndent("    ")?.trim()}")
                null
            }

            if (decodedMessage != null) onMessage(decodedMessage)

            onMessageReturn
        }
    }

    fun send(message: ClientToServer) {
        val socket = socket ?: return
        socket.send(defaultMapper.encodeToString(ClientToServer.serializer(), message))
    }
}
