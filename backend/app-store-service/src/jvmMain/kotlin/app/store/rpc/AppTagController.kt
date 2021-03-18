package dk.sdu.cloud.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.services.ApplicationTagsService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller

class AppTagController (
    private val tagsService: ApplicationTagsService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.createTag) {
            tagsService.createTags(
                request.tags,
                request.applicationName,
                ctx.securityPrincipal
            )
            ok(Unit)
        }

        implement(AppStore.removeTag) {
            tagsService.deleteTags(
                request.tags,
                request.applicationName,
                ctx.securityPrincipal
            )
            ok(Unit)
        }
    }
}
