package dk.sdu.cloud.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.tags
import dk.sdu.cloud.app.store.services.ApplicationSearchService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class AppSearchController (
    private val searchService: ApplicationSearchService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.searchTags) {
            val normalizedExcludeList = request.excludeTools?.split(",") ?: emptyList()
            ok(
                searchService.searchByTags(
                    actorAndProject,
                    request.tags,
                    request.normalize(),
                    normalizedExcludeList
                )
            )
        }

        implement(AppStore.searchApps) {
            ok(searchService.searchApps(actorAndProject, request.query, request.normalize()))
        }

        implement(AppStore.advancedSearch) {
            ok(searchService.advancedSearch(
                actorAndProject,
                request.query,
                request.tags,
                request.showAllVersions,
                request.normalize()
            ))
        }
    }
}

