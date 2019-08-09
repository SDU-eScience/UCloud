package dk.sdu.cloud.notification.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.notification.api.DeleteResponse
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val notificationDAO: NotificationDAO<DBSession>,
    private val subscriptionService: SubscriptionService<DBSession>
) {

    fun listNotifications(
        user: String,
        type: String?,
        since: Long?,
        pagination: NormalizedPaginationRequest
    ) : Page<Notification> {
        return db.withTransaction {
            notificationDAO.findNotifications(
                it,
                user,
                type,
                since,
                pagination
            )
        }
    }

    fun markAsRead(
        user: String,
        ids: List<Long>
    ): List<Long> {
        // TODO Optimize this
        val failedMarkings = mutableListOf<Long>()
        val isTrue: (Boolean) -> Boolean = { it }
        val success = db.withTransaction { session ->
            ids.map { id ->
                val accepted = notificationDAO.markAsRead(session, user, id)
                if (!accepted) failedMarkings.add(id)
                accepted
            }.any(isTrue)

        }
        if (success) return failedMarkings
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not found")
    }

    fun markAllAsRead( user: String) {
        db.withTransaction {
            notificationDAO.markAllAsRead(it, user)
        }
    }

    fun createNotification (
        user: String,
        notification: Notification
    ): FindByNotificationId {
        val result =
            db.withTransaction { FindByNotificationId(notificationDAO.create(it, user, notification)) }

        GlobalScope.launch {
            subscriptionService.onNotification(
                user,
                notification.copy(id = result.id),
                allowRemoteCalls = true
            )
        }

        return result
    }

    fun deleteNotifications (
        ids: List<Long>
    ): List<Long> {
        val failedDeletions = mutableListOf<Long>()
        val isTrue: (Boolean) -> Boolean = { it }
        val success = db.withTransaction { session ->
            ids.map { id ->
                val deleted = notificationDAO.delete(session, id)
                if (!deleted) failedDeletions.add(id)
                deleted
            }.any(isTrue)
        }

        if (success) return failedDeletions.toList()
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not Found")
    }
}
