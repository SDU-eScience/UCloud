package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.service.db.async.DBContext

data class Subscription(val host: HostInfo, val username: String, val id: Long)

interface SubscriptionDao {
    suspend fun open(ctx: DBContext, username: String, hostname: String, port: Int): Long
    suspend fun close(ctx: DBContext, id: Long)
    suspend fun findConnections(ctx: DBContext, username: String): List<Subscription>
    suspend fun refreshSessions(ctx: DBContext, hostname: String, port: Int)
}
