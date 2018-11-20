package dk.sdu.cloud.notification.services

import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.criteria.Expression

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
    override var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    override var modifiedAt: Date,

    @Id
    @GeneratedValue
    var id: Long = 0
) : WithTimestamps {
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

    override fun markAllAsRead(session: HibernateSession, user: String) {
        session.createQuery("update NotificationEntity e set e.read = true where e.owner = :owner")
            .setParameter("owner", user)
            .executeUpdate()
    }
}
