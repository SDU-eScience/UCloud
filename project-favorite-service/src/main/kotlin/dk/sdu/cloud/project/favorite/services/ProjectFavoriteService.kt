package dk.sdu.cloud.project.favorite.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewProjectRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ProjectFavoriteService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val projectFavoriteDAO: ProjectFavoriteDAO<DBSession>,
    private val client: AuthenticatedClient
) {

    suspend fun listFavorites(user: SecurityPrincipal, paging: NormalizedPaginationRequest): Page<String> {
        return db.withTransaction { session ->
            projectFavoriteDAO.listFavorites(
                session,
                user,
                paging
            )
        }
    }

    suspend fun toggleFavorite(projectID: String, user: SecurityPrincipal) {
        val checkedProjectID = Projects.view.call(
            ViewProjectRequest(projectID),
            client
        ).orThrow().id

        db.withTransaction { session ->
            projectFavoriteDAO.toggleFavorite(
                session,
                user,
                checkedProjectID
            )
        }
    }

}
