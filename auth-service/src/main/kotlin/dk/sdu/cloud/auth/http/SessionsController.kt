package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.services.SessionService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.service.Controller

class SessionsController(
    private val sessionService: SessionService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AuthDescriptions.listUserSessions) {
            ok(sessionService.listSessions(ctx.securityToken, request.normalize()))
        }

        implement(AuthDescriptions.invalidateSessions) {
            // TODO FIXME THIS NEEDS TO REMOVE THE EXISTING COOKIE
            sessionService.invalidateSessions(ctx.securityToken)
            ok(Unit)
        }
    }
}
