package dk.sdu.cloud.password.reset.rpc

import dk.sdu.cloud.password.reset.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.password.reset.services.PasswordResetService
import dk.sdu.cloud.service.Loggable
import io.ktor.util.InternalAPI

class PasswordResetController<Session>(passwordResetService: PasswordResetService<Session>) : Controller {
    private val resetService = passwordResetService

    @InternalAPI
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(PasswordResetDescriptions.reset) {
            ok(resetService.createReset(request.email))
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
