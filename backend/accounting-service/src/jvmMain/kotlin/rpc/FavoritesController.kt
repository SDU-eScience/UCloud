package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.services.projects.FavoriteProjectService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.favorite.api.ProjectFavorites
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory

class FavoritesController(
    private val db: AsyncDBSessionFactory,
    private val dao: FavoriteProjectService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ProjectFavorites.toggleFavorite) {
            ok(dao.toggleFavorite(db, ctx.securityPrincipal, request.projectId))
        }

        Unit
    }
}