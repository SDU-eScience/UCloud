package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.AccessTokenContents

interface OpaqueAccessTokenDao<Session> {
    fun find(session: Session, token: String): AccessTokenContents?
    fun insert(session: Session, token: String, contents: AccessTokenContents)
    fun revoke(session: Session, token: String)
}
