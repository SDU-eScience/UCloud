package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.http.CoreAuthController.Companion.REFRESH_WEB_REFRESH_TOKEN_COOKIE
import dk.sdu.cloud.auth.services.SessionService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import io.ktor.application.call

class SessionsController(
    private val sessionService: SessionService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AuthDescriptions.listUserSessions) {
            ok(sessionService.listSessions(ctx.securityToken, request.normalize()))
        }

        implement(AuthDescriptions.invalidateSessions) {
            sessionService.invalidateSessions(ctx.securityToken)

            withContext<HttpCall> {
                ctx.call.response.cookies.appendExpired(
                    REFRESH_WEB_REFRESH_TOKEN_COOKIE,
                    path = "/"
                )
            }

            ok(Unit)
        }
    }
}
