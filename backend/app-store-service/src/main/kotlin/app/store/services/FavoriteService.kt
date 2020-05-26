package app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class FavoriteService () {
    suspend fun toggleFavorite(securityPrincipal: SecurityPrincipal, project: String?, appName: String, appVersion: String) {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        db.withTransaction { session ->
            applicationDAO.toggleFavorite(
                session,
                securityPrincipal,
                project,
                projectGroups,
                appName,
                appVersion
            )
        }
    }

    private suspend fun retrieveUserProjectGroups(user: SecurityPrincipal, project: String): List<String> =
        ProjectMembers.userStatus.call(
            UserStatusRequest(user.username),
            authenticatedClient
        ).orRethrowAs {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }.groups.filter { it.projectId == project }.map { it.group }

    suspend fun retrieveFavorites(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.retrieveFavorites(
                session,
                securityPrincipal,
                project,
                projectGroups as List<String>,
                request.normalize()
            )
        }
    }

}
