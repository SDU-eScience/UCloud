package dk.sdu.cloud.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.tags
import dk.sdu.cloud.app.store.services.ApplicationSearchService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller

class AppSearchController (
    private val searchService: ApplicationSearchService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.searchTags) {
            ok(searchService.searchByTags(ctx.securityPrincipal, ctx.project, request.tags, request.normalize()))
        }

        implement(AppStore.searchApps) {
            ok(searchService.searchApps(ctx.securityPrincipal, ctx.project, request.query, request.normalize()))
        }

        implement(AppStore.advancedSearch) {
            ok(searchService.advancedSearch(
                ctx.securityPrincipal,
                ctx.project,
                request.query,
                request.tags,
                request.showAllVersions,
                request.normalize()
            ))
        }
    }
}

