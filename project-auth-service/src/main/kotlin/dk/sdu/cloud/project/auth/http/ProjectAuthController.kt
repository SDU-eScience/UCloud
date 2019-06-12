package dk.sdu.cloud.project.auth.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.auth.api.ProjectAuthDescriptions
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ProjectAuthController(
    private val tokenRefresher: TokenRefresher<*>,
    private val cloudContext: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ProjectAuthDescriptions.fetchToken) {
            ok(tokenRefresher.refreshTokenForUser(ctx.securityPrincipal.username, userCloud(), request.project))
        }
    }

    private fun CallHandler<*, *, *>.userCloud(): AuthenticatedClient =
        cloudContext.withoutAuthentication()
            .bearerAuth(
                ctx.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            )//.optionallyCausedBy(ctx.jobId)

    companion object : Loggable {
        override val log = logger()
    }
}
