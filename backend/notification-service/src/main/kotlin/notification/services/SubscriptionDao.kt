package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

data class Subscription(
    val host: HostInfo,
    val username: String,
    val id: Long
)

object SubscriptionsTable : SQLTable("subscriptions") {
    val hostname = text("hostname", notNull = true)
    val port = int("port", notNull = true)
    val username = text("username", notNull = true)
    val lastPing = timestamp("last_ping", notNull = true)
    val id = long("id")
}

class SubscriptionDao {
    suspend fun open(ctx: DBContext, username: String, hostname: String, port: Int): Long {
        return ctx.withSession { session ->
            val id = session.allocateId()
            session.insert(SubscriptionsTable) {
                set(SubscriptionsTable.id, id)
                set(SubscriptionsTable.hostname, hostname)
                set(SubscriptionsTable.port, port)
                set(SubscriptionsTable.username, username)
                set(SubscriptionsTable.lastPing, LocalDateTime(Time.now(), DateTimeZone.UTC))
            }
            id
        }
    }

    suspend fun close(ctx: DBContext, id: Long) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                    DELETE FROM subscriptions
                    WHERE id = :id
                """
            )
        }
    }

    suspend fun findConnections(ctx: DBContext, username: String): List<Subscription> {
        val earliestAllowedPing = Time.now() - SubscriptionService.MAX_MS_SINCE_LAST_PING
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("earliest", earliestAllowedPing)
                    },
                    """
                        SELECT * FROM subscriptions
                        WHERE (username = :username) AND (last_ping >= to_timestamp(:earliest))
                    """
                ).rows.map {
                Subscription(
                    HostInfo(
                        host = it.getField(SubscriptionsTable.hostname),
                        port = it.getField(SubscriptionsTable.port)
                    ),
                    it.getField(SubscriptionsTable.username),
                    it.getField(SubscriptionsTable.id
                    )
                ) }
        }

    }

    suspend fun refreshSessions(ctx: DBContext, hostname: String, port: Int) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("newPing", Time.now())
                        setParameter("hostname", hostname)
                        setParameter("port", port)
                    },
                    """
                        UPDATE subscriptions
                        SET last_ping = to_timestamp(:newPing)
                        WHERE (hostname = :hostname) AND (port = :port)
                    """
                )
        }
    }
}
