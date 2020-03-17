package dk.sdu.cloud.password.reset.services

interface ResetRequestsDao<Session> {
    fun create(session: Session, token: String, userId: String)
    fun get(session: Session, token: String): ResetRequest?
}
