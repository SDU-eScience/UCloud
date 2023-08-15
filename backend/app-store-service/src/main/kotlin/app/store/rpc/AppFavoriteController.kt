package dk.sdu.cloud.app.store.rpc

import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.services.FavoriteService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class AppFavoriteController (
    private val favoriteService: FavoriteService
): Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AppStore.toggleFavorite) {
            ok(favoriteService.toggleFavorite(actorAndProject, request.appName, request.appVersion))
        }

        implement(AppStore.retrieveFavorites) {
            ok(favoriteService.retrieveFavorites(actorAndProject, request.normalize()))
        }
    }
}
