package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.project.api.ProjectRole

data class AuthToken(
    val authRefreshToken: String,
    val project: String,
    val role: ProjectRole
)

interface AuthTokenDao<Session> {
    fun storeToken(session: Session, token: AuthToken)
    fun retrieveTokenForProjectInRole(session: Session, project: String, role: ProjectRole): AuthToken
    fun invalidateTokensForProject(session: Session, project: String)
}
