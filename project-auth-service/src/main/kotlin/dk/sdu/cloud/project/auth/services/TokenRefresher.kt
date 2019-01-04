package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.project.auth.api.ProjectAuthenticationToken
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class TokenRefresher<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val tokenDao: AuthTokenDao<DBSession>,
    private val tokenInvalidator: TokenInvalidator<DBSession>
) {
    private val cloudContext = serviceCloud.parent

    suspend fun refreshTokenForUser(username: String, project: String): ProjectAuthenticationToken {
        log.debug("Refreshing token for user=$username, project=$project")

        val role = ProjectDescriptions.viewMemberInProject.call(
            ViewMemberInProjectRequest(
                projectId = project,
                username = username
            ),
            serviceCloud
        ).orThrow().member.role

        log.debug("$username is a $role in $project")
        val token = db.withTransaction { tokenDao.retrieveTokenForProjectInRole(it, project, role) }

        log.debug("Retrieved refresh token for $project/$role. Refreshing at central auth!")
        val accessTokenResponse = AuthDescriptions.refresh
            .call(Unit, cloudContext.jwtAuth(token.authRefreshToken))

        val statusCode = HttpStatusCode.fromValue(accessTokenResponse.status)
        val accessToken = when {
            accessTokenResponse is RESTResponse.Ok -> accessTokenResponse.result.accessToken

            statusCode in setOf(HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized) -> {
                log.warn("$statusCode while refreshing token for user=$username and project=$project")
                log.warn("Invalidating all remaining tokens")
                tokenInvalidator.invalidateTokensForProject(project)

                throw RPCException.fromStatusCode(statusCode)
            }
            else -> throw RPCException.fromStatusCode(statusCode)
        }

        log.debug("Token refreshed for $username/$role")
        return ProjectAuthenticationToken(accessToken)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
