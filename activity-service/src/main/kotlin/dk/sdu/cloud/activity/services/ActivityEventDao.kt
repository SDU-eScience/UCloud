package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType

interface ActivityEventDao<Session> {
    fun findByFileId(
        session: Session,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent>

    fun findByUser(
        session: Session,
        pagination: NormalizedPaginationRequest,
        user: String
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
    indexes = [Index(columnList = "file_id"), Index(columnList = "original_file_name")]
)
sealed class ActivityEventEntity {
    @get:Id
    @get:GeneratedValue
    abstract var id: Long?

    @get:Temporal(TemporalType.TIMESTAMP)
    abstract var timestamp: Date

    @get:Column(name = "file_id")
    abstract var fileId: String

    abstract var username: String

    @get:Column(name = "original_file_name")
    abstract var originalFileName: String

    abstract fun toModel(): ActivityEvent

    @Entity
    data class Download(
        override var username: String,

        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Download(username, timestamp.time, fileId, originalFileName)
    }

    @Entity
    class Updated(
        override var username: String,

        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Updated(username, timestamp.time, fileId, originalFileName)
    }

    @Entity
    class Favorite(
        override var username: String,
        var isFavorite: Boolean,

        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Favorite(username, isFavorite, timestamp.time, fileId, originalFileName)
    }

    @Entity
    class Inspected(
        override var username: String,
        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Inspected(username, timestamp.time, fileId, originalFileName)
    }

    @Entity
    class Moved(
        override var username: String,
        var newName: String,

        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Moved(username, newName, timestamp.time, fileId, originalFileName)
    }

    @Entity
    class Deleted(
        override var username: String,
        override var fileId: String,
        override var originalFileName: String,
        override var id: Long? = null,
        override var timestamp: Date = Date(System.currentTimeMillis())
    ) : ActivityEventEntity() {
        override fun toModel(): ActivityEvent =
            ActivityEvent.Deleted(timestamp.time, fileId, username, originalFileName)
    }

    companion object : HibernateEntity<ActivityEventEntity>, WithId<Long> {
        fun fromEvent(event: ActivityEvent): ActivityEventEntity {
            return when (event) {
                is ActivityEvent.Download ->
                    ActivityEventEntity.Download(
                        username = event.username,
                        fileId = event.fileId,
                        timestamp = Date(event.timestamp),
                        originalFileName = event.originalFilePath
                    )

                is ActivityEvent.Updated ->
                    ActivityEventEntity.Updated(
                        username = event.username,
                        fileId = event.fileId,
                        originalFileName = event.originalFilePath,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Favorite ->
                    ActivityEventEntity.Favorite(
                        username = event.username,
                        isFavorite = event.isFavorite,
                        fileId = event.fileId,
                        originalFileName = event.originalFilePath,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Inspected ->
                    ActivityEventEntity.Inspected(
                        username = event.username,
                        fileId = event.fileId,
                        originalFileName = event.originalFilePath,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Moved ->
                    ActivityEventEntity.Moved(
                        username = event.username,
                        newName = event.newName,
                        fileId = event.fileId,
                        originalFileName = event.originalFilePath,
                        timestamp = Date(event.timestamp)
                    )

                is ActivityEvent.Deleted ->
                    ActivityEventEntity.Deleted(
                        username = event.username,
                        fileId = event.fileId,
                        originalFileName = event.originalFilePath,
                        timestamp = Date(event.timestamp)
                    )
            }
        }
    }
}

private const val LIMIT_OF_QUEUE_SAVES = 50

class HibernateActivityEventDao : ActivityEventDao<HibernateSession> {
    override fun findByFileId(
        session: HibernateSession,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return session.paginatedCriteria<ActivityEventEntity>(
            pagination = pagination,
            orderBy = { listOf(descending(entity[ActivityEventEntity::timestamp])) },
            predicate = { entity[ActivityEventEntity::fileId] equal fileId }
        ).mapItems { it.toModel() }
    }

    override fun findByUser(
        session: HibernateSession,
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<ActivityEvent> {
        return session.paginatedCriteria<ActivityEventEntity>(
            pagination = pagination,
            orderBy = { listOf(descending(entity[ActivityEventEntity::timestamp])) },
            predicate = { entity[ActivityEventEntity::username] equal user }
        ).mapItems { it.toModel() }
    }

    override fun insertBatch(session: HibernateSession, events: List<ActivityEvent>) {
        events.forEachIndexed { index, activityEvent ->
            session.save(ActivityEventEntity.fromEvent(activityEvent))
            if (index % LIMIT_OF_QUEUE_SAVES == 0) {
                session.flush()
                session.clear()
            }
        }
    }

    override fun insert(session: HibernateSession, event: ActivityEvent) {
        session.save(ActivityEventEntity.fromEvent(event))
    }
}
