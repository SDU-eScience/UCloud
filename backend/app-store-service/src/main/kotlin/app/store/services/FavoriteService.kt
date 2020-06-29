package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession

class FavoriteService (
    private val db: AsyncDBSessionFactory,
    private val favoriteDao: FavoriteAsyncDao,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun toggleFavorite(securityPrincipal: SecurityPrincipal, project: String?, appName: String, appVersion: String) {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        db.withSession { session ->
            favoriteDao.toggleFavorite(
                session,
                securityPrincipal,
                project,
                projectGroups,
                appName,
                appVersion
            )
        }
    }

    suspend fun retrieveFavorites(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withSession { session ->
            favoriteDao.retrieveFavorites(
                session,
                securityPrincipal,
                project,
                projectGroups as List<String>,
                request.normalize()
            )
        }
    }

}
