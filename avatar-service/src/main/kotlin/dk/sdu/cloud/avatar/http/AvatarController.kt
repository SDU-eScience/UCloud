package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.avatar.api.FindResponse
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route
import org.hibernate.Session

class AvatarController<DBSession> (
    private val avatarService: AvatarService<DBSession>
) : Controller {
    override val baseContext = AvatarDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AvatarDescriptions.create) { req ->
            avatarService.insert(req)
            ok(Unit)
        }

        implement(AvatarDescriptions.update) {

            ok(Unit)
        }

        implement(AvatarDescriptions.findAvatar) {

            ok(FindResponse("1","2","3","4","5","6","7","8","9","10","11","12"))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
