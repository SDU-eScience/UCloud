package dk.sdu.cloud.notification.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.jsonb
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object NotificationTable : SQLTable("notification.notifications") {
    val type = text("type", notNull = true)
    val message = text("message", notNull = true)
    val owner = text("owner", notNull = true)
    val meta = jsonb("meta")
    val read = bool("read", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val id = long("id")
}

class NotificationHibernateDAO : NotificationDAO {
    override suspend fun findNotifications(
        ctx: DBContext,
        user: String,
        type: String?,
        since: Long?,
        paginationRequest: NormalizedPaginationRequest
    ): Page<Notification> {
        return ctx.withSession { session ->
            val itemsInTotal = session.sendPreparedStatement(
                {
                    setParameter("owner", user)
                    setParameter("since", since)
                    setParameter("type", type)
                },
                """
                    SELECT COUNT(*)
                    FROM notification.notifications
                    WHERE (
                        (owner = ?owner) 
                        AND
                        (
                            ?since::bigint is null or
                            created_at >= to_timestamp(?since)
                        )
                        AND
                        (
                            ?type::text is null or
                            type = ?type 
                        )
                    )
                """.trimIndent()
            )
            val item = session.paginatedQuery(
                paginationRequest,
                {
                    setParameter("owner", user)
                    setParameter("since", since)
                    setParameter("type", type)
                },
                """
                    FROM notification.notifications
                    WHERE (
                        (owner = ?owner) 
                        AND
                        (
                            ?since::bigint is null or
                            created_at >= to_timestamp(?since)
                        )
                        AND
                        (
                            ?type::text is null or
                            type = ?type 
                        )
                    )
                    GROUP BY created_at, id
                    ORDER BY created_at DESC
                """.trimIndent()
            ).mapItems { it.toNotification() }
            //Need to be done since test DB required GROUPBY and that ruined the pageQuery function. Multiple count rows
            //resulting in itemsInTotal was set to 0. Possible fix is to sum on the rows.
            item.copy(itemsInTotal = itemsInTotal.rows.singleOrNull()?.getLong(0)?.toInt() ?: 0)
        }
    }

    override suspend fun create(ctx: DBContext, user: String, notification: Notification): NotificationId {
        val id = ctx.withSession{ it.allocateId("notification.hibernate_sequence")}
        println("ID: $id")
        ctx.withSession{ session ->
            session.insert(NotificationTable) {
                set(NotificationTable.owner, user)
                set(NotificationTable.message, notification.message)
                set(NotificationTable.meta, defaultMapper.writeValueAsString(notification.meta))
                set(NotificationTable.read, notification.read)
                set(NotificationTable.createdAt, LocalDateTime.now(DateTimeZone.UTC))
                set(NotificationTable.modifiedAt, LocalDateTime.now(DateTimeZone.UTC))
                set(NotificationTable.type, notification.type)
                set(NotificationTable.id, id)
            }
        }
        return id
    }

    override suspend fun delete(ctx: DBContext, id: NotificationId): Boolean {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                    DELETE FROM notification.notifications where id=?id
                    
                """.trimIndent()
            )
        }.rowsAffected > 0
    }

    override suspend fun markAsRead(ctx: DBContext, user: String, id: NotificationId): Boolean {
        return ctx.withSession{ session ->
            session.sendPreparedStatement(
                {
                    setParameter("read", true)
                    setParameter("id", id)
                    setParameter("owner", user)
                },
                """
                    UPDATE notification.notifications
                    SET read = ?read
                    WHERE id = ?id and owner = ?owner
                """.trimIndent()
            ).rowsAffected > 0
        }
    }

    override suspend fun markAllAsRead(ctx: DBContext, user: String) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("read", true)
                    setParameter("owner", user)
                },
                """
                    UPDATE notification.notifications
                    SET read = ?read
                    WHERE owner = ?owner
                """.trimIndent()
            )
        }
    }

    private fun RowData.toNotification() : Notification {
        val meta = runCatching {
            defaultMapper.readValue<Map<String, Any?>>(getField(NotificationTable.meta))
        }.getOrNull() ?: run {
            log.warn(
                "Unable to parse metadata for: " +
                        "${getField(NotificationTable.id)}, " +
                        "${getField(NotificationTable.message)}"
            )

            emptyMap<String, Any?>()
        }

        return Notification(
            getField(NotificationTable.type),
            getField(NotificationTable.message),
            getField(NotificationTable.id),
            meta,
            getField(NotificationTable.createdAt).toDate().time,
            getField(NotificationTable.read)
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
