package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode

data class AuthToken(
    val authRefreshToken: String,
    val project: String,
    val role: ProjectRole
)

interface AuthTokenDao<Session> {
    fun storeToken(session: Session, token: AuthToken)
    fun retrieveTokenForProjectInRole(session: Session, project: String, role: ProjectRole): AuthToken
    fun invalidateTokensForProject(session: Session, project: String)
    fun tokensForProject(session: Session, project: String): List<AuthToken>
}

sealed class AuthTokenException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : AuthTokenException("Not found", HttpStatusCode.NotFound)
}
