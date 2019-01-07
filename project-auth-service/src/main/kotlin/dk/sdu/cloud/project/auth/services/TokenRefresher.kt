package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.project.auth.api.ProjectAuthenticationToken
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orRethrowAs
import dk.sdu.cloud.service.orThrow
import io.ktor.http.HttpStatusCode

class TokenRefresher<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val tokenDao: AuthTokenDao<DBSession>,
    private val tokenInvalidator: TokenInvalidator<DBSession>
) {
    private val cloudContext = serviceCloud.parent

    suspend fun refreshTokenForUser(
        username: String,
        userCloud: AuthenticatedCloud,
        project: String
    ): ProjectAuthenticationToken {
        log.debug("Refreshing token for user=$username, project=$project")

        val role = ProjectDescriptions.viewMemberInProject.call(
            ViewMemberInProjectRequest(
                projectId = project,
                username = username
            ),
            serviceCloud
        ).orThrow().member.role

        log.debug("$username is a $role in $project")
        val refreshToken = db.withTransaction { tokenDao.retrieveTokenForProjectInRole(it, project, role) }

        val userToken = run {
            log.debug("Retrieved refresh token for $project/$role. Refreshing at central auth!")
            val accessTokenResponse = AuthDescriptions.refresh
                .call(Unit, cloudContext.jwtAuth(refreshToken.authRefreshToken))

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

            log.debug("Extending token for user...")
            AuthDescriptions.tokenExtension.call(
                TokenExtensionRequest(
                    validJWT = accessToken,
                    requestedScopes = listOf(SecurityScope.ALL_SCOPE),
                    expiresIn = 1000 * 60 * 5L,
                    allowRefreshes = false
                ),
                userCloud
            ).orRethrowAs {
                log.warn("Caught and exception while extending token for user (${it.status}: ${it.error})")
                throw AuthTokenException.InternalError()
            }.accessToken
        }

        log.debug("Token refreshed for $username/$role")
        return ProjectAuthenticationToken(userToken)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
