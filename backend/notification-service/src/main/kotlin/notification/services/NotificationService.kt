package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NotificationService(
    private val db: DBContext,
    private val notificaions: NotificationDao,
    private val subscriptionService: SubscriptionService
) {

    suspend fun listNotifications(
        user: String,
        type: String?,
        since: Long?,
        pagination: NormalizedPaginationRequest
    ): Page<Notification> {
        return db.withSession {
            notificaions.findNotifications(
                it,
                user,
                type,
                since,
                pagination
            )
        }
    }

    suspend fun markAsRead(
        user: String,
        ids: List<Long>
    ): List<Long> {
        // TODO Optimize this
        val failedMarkings = mutableListOf<Long>()
        val isTrue: (Boolean) -> Boolean = { it }
        val success = db.withSession { session ->
            ids.map { id ->
                val accepted = notificaions.markAsRead(session, user, id)
                if (!accepted) failedMarkings.add(id)
                accepted
            }.any(isTrue)

        }
        if (success) return failedMarkings
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not found")
    }

    suspend fun markAllAsRead(user: String) {
        db.withSession {
            notificaions.markAllAsRead(it, user)
        }
    }

    suspend fun createNotification(
        user: String,
        notification: Notification
    ): FindByNotificationId {
        val result =
            db.withSession { FindByNotificationId(notificaions.create(it, user, notification)) }

        GlobalScope.launch {
            subscriptionService.onNotification(
                user,
                notification.copy(id = result.id),
                allowRemoteCalls = true
            )
        }

        return result
    }

    suspend fun deleteNotifications(
        ids: List<Long>
    ): List<Long> {
        val failedDeletions = mutableListOf<Long>()
        val isTrue: (Boolean) -> Boolean = { it }
        val success = db.withSession { session ->
            ids.map { id ->
                val deleted = notificaions.delete(session, id)
                if (!deleted) failedDeletions.add(id)
                deleted
            }.any(isTrue)
        }

        if (success) return failedDeletions.toList()
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not Found")
    }
}
