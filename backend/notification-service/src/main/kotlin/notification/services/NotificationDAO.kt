package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext

val FIRST_PAGE = NormalizedPaginationRequest(null, null)

interface NotificationDAO {
    /**
     * Retrieves a page of notifications. The notifications must be sorted by descending time stamp
     *
     * @param user The user for which the notifications should be retrieved
     * @param type The type of notification. If null all types will be accepted
     * @param since Don't return notifications from before this timestamp
     * @param paginationRequest Controls pagination of results
     */
    suspend fun findNotifications(
        ctx: DBContext,
        user: String,
        type: String? = null,
        since: Long? = null,
        paginationRequest: NormalizedPaginationRequest = FIRST_PAGE
    ): Page<Notification>

    /**
     * Creates a [notification] for [user]
     *
     * The [Notification.id] parameter is ignored.
     *
     * @return The ID of the newly created notification
     */
    suspend fun create(ctx: DBContext, user: String, notification: Notification): NotificationId

    /**
     * Deletes a notification with [id]
     *
     * @return `true` if the notification exists and was deleted, `false` if the notification does not exist
     */
    suspend fun delete(ctx: DBContext, id: NotificationId): Boolean

    /**
     * Marks a notification with [id] for [user] as read
     *
     * @return `true` if the notification exists and was marked as read. `false` will be returned if the
     * notification doesn't exist
     */
    suspend fun markAsRead(ctx: DBContext, user: String, id: NotificationId): Boolean

    suspend fun markAllAsRead(ctx: DBContext, user: String)
}
