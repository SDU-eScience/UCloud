package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import java.util.*
import kotlin.math.min

val FIRST_PAGE = NormalizedPaginationRequest(null, null)

interface NotificationDAO {
    fun findNotifications(
        user: String,
        type: String? = null,
        since: Long? = null,
        paginationRequest: NormalizedPaginationRequest = FIRST_PAGE
    ): Page<Notification>

    fun create(user: String, notification: Notification): NotificationId

    fun delete(id: NotificationId): Boolean

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