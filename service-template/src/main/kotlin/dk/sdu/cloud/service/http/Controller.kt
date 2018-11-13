package dk.sdu.cloud.{{ serviceName }}.http

import dk.sdu.cloud.{{ serviceName }}.api.{{ serviceNameTitle }}Descriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class {{ serviceNameTitle }}Controller : Controller {
    override val baseContext = {{ serviceNameTitle }}Descriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement({{ serviceNameTitle }}Descriptions.call) {
            // Implement call here
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
