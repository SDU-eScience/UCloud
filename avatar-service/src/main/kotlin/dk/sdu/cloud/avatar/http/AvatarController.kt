package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class AvatarController : Controller {
    override val baseContext = AvatarDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AvatarDescriptions.create) {
            // Implement call here
            ok(Unit)
        }

        implement(AvatarDescriptions.update) {

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
