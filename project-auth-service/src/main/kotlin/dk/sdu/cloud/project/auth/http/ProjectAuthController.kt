package dk.sdu.cloud.project.auth.http

import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class ProjectAuthController(
    private val tokenRefresher: TokenRefresher<*>
) : Controller {
    override val baseContext = ProjectAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ProjectAuthDescriptions.fetchToken) { req ->
            ok(tokenRefresher.refreshTokenForUser(call.securityPrincipal.username, req.project))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
