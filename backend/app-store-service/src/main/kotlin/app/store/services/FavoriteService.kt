package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PaginationRequest
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession

class FavoriteService (
    private val db: AsyncDBSessionFactory,
    private val favoriteDao: FavoriteAsyncDao,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun toggleFavorite(actorAndProject: ActorAndProject, appName: String, appVersion: String) {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        db.withSession { session ->
            favoriteDao.toggleFavorite(
                session,
                actorAndProject,
                projectGroups,
                appName,
                appVersion
            )
        }
    }

    suspend fun retrieveFavorites(
        actorAndProject: ActorAndProject,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.withSession { session ->
            favoriteDao.retrieveFavorites(
                session,
                actorAndProject,
                projectGroups as List<String>,
                request.normalize()
            )
        }
    }

}
