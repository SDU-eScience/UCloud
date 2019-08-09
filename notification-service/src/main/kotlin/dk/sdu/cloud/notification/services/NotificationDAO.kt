package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

val FIRST_PAGE = NormalizedPaginationRequest(null, null)

interface NotificationDAO<Session> {
    /**
     * Retrieves a page of notifications. The notifications must be sorted by descending time stamp
     *
     * @param user The user for which the notifications should be retrieved
     * @param type The type of notification. If null all types will be accepted
     * @param since Don't return notifications from before this timestamp
     * @param paginationRequest Controls pagination of results
     */
    fun findNotifications(
        session: Session,
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
    fun create(session: Session, user: String, notification: Notification): NotificationId

    /**
     * Deletes a notification with [id]
     *
     * @return `true` if the notification exists and was deleted, `false` if the notification does not exist
     */
    fun delete(session: Session, id: NotificationId): Boolean

    /**
     * Marks a notification with [id] for [user] as read
     *
     * @return `true` if the notification exists and was marked as read. `false` will be returned if the
     * notification doesn't exist
     */
    fun markAsRead(session: Session, user: String, id: NotificationId): Boolean

    fun markAllAsRead(session: Session, user: String)
}
