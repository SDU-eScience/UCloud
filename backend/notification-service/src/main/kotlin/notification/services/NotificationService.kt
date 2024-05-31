package dk.sdu.cloud.notification.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.notification.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

class NotificationService(
    private val db: DBContext,
    private val notifications: NotificationDao,
    private val subscriptionService: SubscriptionService
) {

    suspend fun listNotifications(
        user: String,
        type: String?,
        since: Long?,
        pagination: NormalizedPaginationRequest
    ): Page<Notification> {
        return db.withSession {
            notifications.findNotifications(
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
                val accepted = notifications.markAsRead(session, user, id)
                if (!accepted) failedMarkings.add(id)
                accepted
            }.any(isTrue)

        }
        if (success) return failedMarkings
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not found")
    }

    suspend fun markAllAsRead(user: String) {
        db.withSession {
            notifications.markAllAsRead(it, user)
        }
    }

    suspend fun createNotification(
        user: String,
        notification: Notification
    ): CreateNotificationResponse {
        return createNotifications(listOf(CreateNotification(user, notification))).single()
    }

    suspend fun createNotifications(
        batch: List<CreateNotification>
    ): List<CreateNotificationResponse> {
        val result = db.withSession { session ->
            batch.map { (user, notification) ->
                if (!wantNotification(user, notification.type)) {
                    null
                } else {
                    FindByNotificationId(notifications.create(session, user, notification))
                }
            }
        }

        GlobalScope.launch {
            batch.zip(result).filter { it.second != null }.forEach { (input, findById) ->
                val (user, notification) = input
                subscriptionService.onNotification(
                    user,
                    notification.copy(id = findById!!.id),
                    allowRemoteCalls = true
                )
            }
        }

        return result.map { CreateNotificationResponse(it) }
    }

    suspend fun deleteNotifications(
        ids: List<Long>
    ): List<Long> {
        val failedDeletions = mutableListOf<Long>()
        val isTrue: (Boolean) -> Boolean = { it }
        val success = db.withSession { session ->
            ids.map { id ->
                val deleted = notifications.delete(session, id)
                if (!deleted) failedDeletions.add(id)
                deleted
            }.any(isTrue)
        }

        if (success) return failedDeletions.toList()
        else throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Not Found")
    }

    suspend fun updateSettings(
        username: String,
        request: NotificationSettings
    ) {
        val json = defaultMapper.encodeToJsonElement(request)
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                setParameter("json", json.toString())
                setParameter("username", username)
                },
                """
                    INSERT INTO notification_settings (username, settings) 
                    VALUES (:username, :json::jsonb) 
                    ON CONFLICT (username)
                    DO UPDATE SET settings = :json::jsonb
                """
            )
        }
    }

    suspend fun retrieveSettings(
        username: String
    ): NotificationSettings {
        return db.withSession { session ->
            val settings = session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    SELECT settings
                    FROM notification_settings
                    WHERE username = :username
                """
            ).rows
                .singleOrNull()
                ?.getString(0)
            if (settings == null) {
                NotificationSettings()
            }
            else {
                defaultMapper.decodeFromString<NotificationSettings>(settings)
            }
        }
    }

    private suspend fun wantNotification(
        username: String,
        type: String
    ): Boolean {
        val settings = retrieveSettings(username)

        return when (type) {
            "JOB_STARTED" -> settings.jobStarted
            "JOB_COMPLETED" -> settings.jobStopped
            "JOB_EXPIRED" -> settings.jobStopped
            "JOB_FAILED" -> settings.jobStopped
            else -> true
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
