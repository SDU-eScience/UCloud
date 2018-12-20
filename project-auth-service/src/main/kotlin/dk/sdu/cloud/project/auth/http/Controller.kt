package dk.sdu.cloud.project.auth.http

import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

class ProjectAuthController : Controller {
    override val baseContext = ProjectAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ProjectAuthDescriptions.call) {
            // Implement call here
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
