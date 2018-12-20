package dk.sdu.cloud.project.auth.services

data class UserToken(
    val token: String,
    val username: String,
    val project: String
)

interface UserTokenDao<Session> {
    fun storeToken(session: Session, token: UserToken)
    fun validateToken(session: Session, token: String): UserToken
    fun invalidateTokensForProject(session: Session, project: String)
    fun invalidateTokensForUser(session: Session, username: String)
}
