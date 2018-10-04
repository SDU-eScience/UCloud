package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import java.util.*
import javax.persistence.*

interface ActivityEventDao<Session> {
    fun findByFileId(
        session: Session,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent>

    fun insertBatch(
        session: Session,
        events: List<ActivityEvent>
    ) {
        events.forEach { insert(session, it) }
    }

    fun insert(
        session: Session,
        event: ActivityEvent
    )
}

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(
    name = "activity_events",
    indexes = [Index(columnList = "file_id")]
)
sealed class ActivityEventEntity {
    @get:Id
    @get:GeneratedValue
    abstract var id: Long?

    @get:Temporal(TemporalType.TIMESTAMP)
    abstract var timestamp: Date

    @get:Column(name = "file_id")
    abstract var fileId: String

    abstract fun toModel(): ActivityEvent

    @Entity
    class Download(
        var username: String,

        override var fileId: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Download(username, timestamp.time, fileId)
    }

    @Entity
    class Updated(
        var username: String,

        override var fileId: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Updated(username, timestamp.time, fileId)
    }

    @Entity
    class Favorite(
        var username: String,
        var isFavorite: Boolean,

        override var fileId: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Favorite(username, isFavorite, timestamp.time, fileId)
    }

    @Entity
    class Inspected(
        var username: String,
        override var fileId: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Inspected(username, timestamp.time, fileId)
    }

    @Entity
    class Moved(
        var username: String,
        var newName: String,

        override var fileId: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Moved(username, newName, timestamp.time, fileId)
    }

    @Entity
    class Deleted(
        var username: String,
        override var fileId: String,
        override var timestamp: Date,
        override var id: Long? = null
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent = ActivityEvent.Deleted(timestamp.time, fileId, username)
    }

    companion object : HibernateEntity<ActivityEventEntity>, WithId<Long> {
        fun fromEvent(event: ActivityEvent): ActivityEventEntity {
            return when (event) {
                is ActivityEvent.Download ->
                    ActivityEventEntity.Download(event.username, event.fileId, timestamp = Date(event.timestamp))

                is ActivityEvent.Updated ->
                    ActivityEventEntity.Updated(event.username, event.fileId, timestamp = Date(event.timestamp))

                is ActivityEvent.Favorite ->
                    ActivityEventEntity.Favorite(
                        event.username, event.isFavorite, event.fileId,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Inspected ->
                    ActivityEventEntity.Inspected(event.username, event.fileId, timestamp = Date(event.timestamp))

                is ActivityEvent.Moved ->
                    ActivityEventEntity.Moved(
                        event.username, event.newName, event.fileId,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Deleted ->
                    ActivityEventEntity.Deleted(
                        event.username, event.fileId, timestamp = Date(event.timestamp)
                    )
            }
        }
    }
}

class HibernateActivityEventDao : ActivityEventDao<HibernateSession> {
    override fun findByFileId(
        session: HibernateSession,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return session.paginatedCriteria<ActivityEventEntity>(pagination) {
            entity[ActivityEventEntity::fileId] equal fileId
        }.mapItems { it.toModel() }
    }

    override fun insertBatch(session: HibernateSession, events: List<ActivityEvent>) {
        events.forEachIndexed { index, activityEvent ->
            session.save(ActivityEventEntity.fromEvent(activityEvent))
            if (index % 50 == 0) {
                session.flush()
                session.clear()
            }
        }
    }

    override fun insert(session: HibernateSession, event: ActivityEvent) {
        session.save(ActivityEventEntity.fromEvent(event))
    }
}