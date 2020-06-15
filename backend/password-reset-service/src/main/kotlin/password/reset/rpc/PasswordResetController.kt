package dk.sdu.cloud.password.reset.rpc

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.password.reset.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.password.reset.services.PasswordResetService
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class PasswordResetController(private val resetService: PasswordResetService) : Controller {

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(PasswordResetDescriptions.reset) {
            ok(resetService.createResetRequest(request.email))
        }

        implement(PasswordResetDescriptions.newPassword) {
            ok(resetService.newPassword(request.token, request.newPassword))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
