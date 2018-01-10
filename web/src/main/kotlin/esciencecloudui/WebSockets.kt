import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import esciencecloudui.Notification
import io.ktor.routing.Route
import io.ktor.websocket.Frame
import io.ktor.websocket.webSocket

fun Route.webSockets() {
    webSocket("/analyses") {

    }
    webSocket("/activity") {

    }
    webSocket("/notifications") {
        (0..25).forEach { i ->
            val notification = Notification("Message $i", "This is body $i, sent using websockets", System.currentTimeMillis() + i * 1000, "Complete", "$i" )
            val notificationString = jacksonObjectMapper().writeValueAsString(notification)
            send(Frame.Text(notificationString))
        }
    }
}