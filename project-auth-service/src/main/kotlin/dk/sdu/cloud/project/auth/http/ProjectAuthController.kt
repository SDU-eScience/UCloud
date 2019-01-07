package dk.sdu.cloud.project.auth.http

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.jobId
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.service.withCausedBy
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class ProjectAuthController(
    private val tokenRefresher: TokenRefresher<*>,
    private val cloudContext: CloudContext
) : Controller {
    override val baseContext = ProjectAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ProjectAuthDescriptions.fetchToken) { req ->
            ok(tokenRefresher.refreshTokenForUser(call.securityPrincipal.username, call.userCloud(), req.project))
        }
    }

    private fun ApplicationCall.userCloud(): AuthenticatedCloud =
        cloudContext
            .jwtAuth(
                request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            ).withCausedBy(request.jobId)

    companion object : Loggable {
        override val log = logger()
    }
}
