package dk.sdu.cloud.project.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class MembershipService(
    private val db: DBSessionFactory<AsyncDBConnection>,
    private val groups: GroupDao,
    private val projects: ProjectDao
) {
    suspend fun summarizeMembershipForUser(username: String): UserStatusResponse {
        return db.withTransaction { session ->
            UserStatusResponse(
                projects.listProjectsForUser(session, null, username).items,
                groups.listGroupsForUser(session, null, username).items
            )
        }
    }

    suspend fun search(
        securityPrincipal: SecurityPrincipal,
        project: String,
        query: String,
        pagination: NormalizedPaginationRequest
    ): Page<String> {
        return db.withTransaction { session ->
            val isAdmin = projects.findRoleOfMember(session, project, securityPrincipal.username)?.isAdmin() == true
            if (isAdmin) {
                groups.searchForMembers(session, project, query, pagination)
            } else {
                null
            }
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
