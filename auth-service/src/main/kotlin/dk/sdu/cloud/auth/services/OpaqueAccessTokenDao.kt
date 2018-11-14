package dk.sdu.cloud.auth.services

interface OpaqueAccessTokenDao<Session> {
    fun find(session: Session, token: String): AccessTokenContents?
    fun insert(session: Session, token: String, contents: AccessTokenContents)
    fun revoke(session: Session, token: String)
}
