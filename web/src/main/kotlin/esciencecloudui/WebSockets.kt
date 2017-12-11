import esciencecloudui.requireAuthentication
import io.ktor.routing.Route
import io.ktor.websocket.webSocket

fun Route.webSockets() {
    requireAuthentication()
    webSocket("/analyses") {

    }
    webSocket("/activity") {

    }
}