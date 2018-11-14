package dk.sdu.cloud.share.http

import dk.sdu.cloud.share.api.ShareDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class ShareController : Controller {
    override val baseContext = ShareDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {

    }

    companion object : Loggable {
        override val log = logger()
    }
}
