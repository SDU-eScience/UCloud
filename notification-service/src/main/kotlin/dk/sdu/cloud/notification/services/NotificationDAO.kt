package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import java.util.*
import kotlin.math.min

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
    fun findNotifications(
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
    fun create(user: String, notification: Notification): NotificationId

    /**
     * Deletes a notification with [id]
     *
     * @return `true` if the notification exists and was deleted, `false` if the notification does not exist
     */
    fun delete(id: NotificationId): Boolean

    /**
     * Marks a notification with [id] for [user] as read
     *
     * @return `true` if the notification exists and was marked as read. `false` will be returned if the
     * notification doesn't exist
     */
    fun markAsRead(user: String, id: NotificationId): Boolean
}

class InMemoryNotificationDAO : NotificationDAO {
    private val inMemoryDb = HashMap<String, List<Notification>>()

    override fun findNotifications(
        user: String,
        type: String?,
        since: Long?,
        paginationRequest: NormalizedPaginationRequest
    ): Page<Notification> {
        val notificationsForUser = inMemoryDb[user] ?: return Page(0, 10, 0, emptyList())
        val allNotifications = notificationsForUser
            .filter {
                if (type == null) true
                else type == it.type
            }
            .filter {
                if (since == null) true
                else it.ts >= since
            }
            .sortedByDescending { it.ts }

        val startIndex = paginationRequest.itemsPerPage * paginationRequest.page
        val page =
            if (startIndex > allNotifications.size) emptyList()
            else allNotifications.subList(
                startIndex,
                min(allNotifications.size, startIndex + paginationRequest.itemsPerPage)
            )

        return Page(allNotifications.size, paginationRequest.itemsPerPage, paginationRequest.page, page)
    }

    override fun create(user: String, notification: Notification): NotificationId {
        val id = UUID.randomUUID().toString()
        val list = inMemoryDb[user] ?: emptyList()
        inMemoryDb[user] = list + listOf(notification.copy(id = id))
        return id
    }

    override fun delete(id: NotificationId): Boolean {
        var found = false
        for ((user, list) in inMemoryDb) {
            val size = inMemoryDb[user]?.size ?: 0
            inMemoryDb[user] = list.filter { it.id != id }
            val newSize = inMemoryDb[user]?.size ?: 0
            if (size != newSize) {
                found = true
                break
            }
        }
        return found
    }

    override fun markAsRead(user: String, id: NotificationId): Boolean {
        var found = false
        inMemoryDb[user] = (inMemoryDb[user] ?: emptyList()).map {
            if (it.id == id) {
                found = true
                it.copy(read = true)
            } else {
                it
            }
        }
        return found
    }
}