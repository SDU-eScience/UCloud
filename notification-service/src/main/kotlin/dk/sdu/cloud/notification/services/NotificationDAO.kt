package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*
import javax.persistence.criteria.Expression
import kotlin.math.min

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
}

@Entity
@Table(name = "notifications")
class NotificationEntity(
    var type: String,

    var message: String,

    var owner: String,

    @Type(type = JSONB_TYPE)
    var meta: Map<String, Any?>,

    var read: Boolean,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<NotificationEntity>, WithId<Long>
}

fun NotificationEntity.toModel(): Notification = Notification(type, message, id, meta, createdAt.time, read)

class NotificationHibernateDAO : NotificationDAO<HibernateSession> {
    override fun findNotifications(
        session: HibernateSession,
        user: String,
        type: String?,
        since: Long?,
        paginationRequest: NormalizedPaginationRequest
    ): Page<Notification> {
        return session.paginatedCriteria<NotificationEntity>(
            paginationRequest,
            orderBy = { listOf(descinding(entity[NotificationEntity::createdAt])) }
        ) {
            val typePredicate: Expression<Boolean> =
                if (type == null) literal(true) else entity[NotificationEntity::type] equal type

            val sincePredicate: Expression<Boolean> =
                if (since == null) literal(true) else entity[NotificationEntity::createdAt] greaterThan Date(since)

            val userPredicate: Expression<Boolean> = entity[NotificationEntity::owner] equal user

            allOf(
                typePredicate.toPredicate(),
                sincePredicate.toPredicate(),
                userPredicate.toPredicate()
            )
        }.mapItems {
            it.toModel()
        }
    }

    override fun create(session: HibernateSession, user: String, notification: Notification): NotificationId {
        val entity = with(notification) {
            NotificationEntity(type, message, user, meta, read, Date(), Date())
        }

        return session.save(entity) as Long
    }

    override fun delete(session: HibernateSession, id: NotificationId): Boolean {
        val entity = NotificationEntity[session, id] ?: return false
        session.delete(entity)
        return true
    }

    override fun markAsRead(session: HibernateSession, user: String, id: NotificationId): Boolean {
        val entity = NotificationEntity[session, id]?.takeIf { it.owner == user } ?: return false
        entity.read = true
        session.update(entity)
        return true
    }
}

class InMemoryNotificationDAO : NotificationDAO<Any> {
    private val inMemoryDb = HashMap<String, List<Notification>>()

    override fun findNotifications(
        session: Any,
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

    override fun create(session: Any, user: String, notification: Notification): NotificationId {
        val id = UUID.randomUUID().mostSignificantBits
        val list = inMemoryDb[user] ?: emptyList()
        inMemoryDb[user] = list + listOf(notification.copy(id = id))
        return id
    }

    override fun delete(session: Any, id: NotificationId): Boolean {
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

    override fun markAsRead(session: Any, user: String, id: NotificationId): Boolean {
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