import esciencecloudui.requireAuthentication
import io.ktor.routing.Route
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.readText
import io.ktor.websocket.webSocket
import kotlinx.coroutines.experimental.channels.consumeEach

fun Route.webSockets() {
    requireAuthentication()
    webSocket("/analyses") {
        send(Frame.Text("[Hello] there."))
    }
}