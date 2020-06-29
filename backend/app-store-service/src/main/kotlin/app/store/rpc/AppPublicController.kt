package dk.sdu.cloud.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.IsPublicResponse
import dk.sdu.cloud.app.store.services.ApplicationPublicService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller

class AppPublicController (
    private val publicService: ApplicationPublicService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.isPublic) {
            ok(IsPublicResponse(publicService.isPublic(ctx.securityPrincipal, request.applications)))
        }

        implement(AppStore.setPublic) {
            ok(publicService.setPublic(ctx.securityPrincipal, request.appName, request.appVersion, request.public))
        }

    }
}

