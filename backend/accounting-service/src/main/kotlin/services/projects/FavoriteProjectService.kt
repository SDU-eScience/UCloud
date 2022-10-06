package dk.sdu.cloud.accounting.services.projects

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService as P2Service

class FavoriteProjectService(private val p2: P2Service) {
    suspend fun toggleFavorite(ctx: DBContext, user: SecurityPrincipal, projectId: String) {
        p2.toggleFavorite(
            ActorAndProject(Actor.User(user), projectId),
            bulkRequestOf(FindByStringId(projectId)),
            ctx = ctx
        )
    }
}


