package dk.sdu.cloud.alerting.http

import dk.sdu.cloud.alerting.api.AlertingDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class AlertingController : Controller {
    override val baseContext = AlertingDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AlertingDescriptions.call) {
            // Implement call here
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
