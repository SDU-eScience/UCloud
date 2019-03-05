package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.client.HostInfo

data class Subscription(val host: HostInfo, val username: String, val id: Long)

interface SubscriptionDao<Session> {
    fun open(session: Session, username: String, hostname: String, port: Int): Long
    fun close(session: Session, id: Long)
    fun findConnections(session: Session, username: String): List<Subscription>
    fun refreshSessions(session: Session, hostname: String, port: Int)
}
