package dk.sdu.cloud.app.license.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Loggable

class AppLicenseController(appLicenseService: AppLicenseService) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppLicenseDescriptions.permission) {
            val entity = ctx.securityPrincipal.username

            appLicenseService.hasPermission(entity, request.appName, request.appVersion)

            ok(PermissionResponse(request.message))
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}