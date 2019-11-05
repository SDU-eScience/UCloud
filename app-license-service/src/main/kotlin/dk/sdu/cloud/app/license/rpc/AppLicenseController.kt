package dk.sdu.cloud.app.license.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Loggable

class AppLicenseController : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppLicenseDescriptions.example) {
            val user = ctx.securityPrincipal.username
            log.info("We automatically log calls and user (but this is how you do it $user")

            ok(ExampleResponse(request.message))
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}