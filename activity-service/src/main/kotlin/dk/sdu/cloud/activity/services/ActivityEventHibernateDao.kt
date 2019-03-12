package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
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
import javax.persistence.criteria.Order
import javax.persistence.criteria.Predicate

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

    @get:Column(name = "dtype", insertable = false, updatable = false)
    open var dtype: String? = null

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
            ActivityEvent.Download(username, timestamp.time, fileId, originalFileName, id)
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
            ActivityEvent.Updated(username, timestamp.time, fileId, originalFileName, id)
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
            ActivityEvent.Favorite(username, isFavorite, timestamp.time, fileId, originalFileName, id)
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
            ActivityEvent.Inspected(username, timestamp.time, fileId, originalFileName, id)
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
            ActivityEvent.Moved(username, newName, timestamp.time, fileId, originalFileName, id)
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
            ActivityEvent.Deleted(timestamp.time, fileId, username, originalFileName, id)
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

    private fun CriteriaBuilderContext<*, ActivityEventEntity>.ordering(): List<Order> {
        return listOf(
            descending(entity[ActivityEventEntity::timestamp]),
            ascending(entity[ActivityEventEntity::id])
        )
    }

    private fun CriteriaBuilderContext<*, ActivityEventEntity>.applyFilter(
        filter: ActivityEventFilter
    ): Predicate {
        var builder: Predicate? = null

        fun addCriteria(predicate: Predicate) {
            val captured = builder

            builder = if (captured == null) predicate
            else captured and predicate
        }

        with(filter) {
            if (type != null) {
                val rawDType = when (type) {
                    ActivityEventType.download -> "Download"
                    ActivityEventType.updated -> "Updated"
                    ActivityEventType.deleted -> "Deleted"
                    ActivityEventType.favorite -> "Favorite"
                    ActivityEventType.inspected -> "Inspected"
                    ActivityEventType.moved -> "Moved"
                }

                val dtype = "ActivityEventEntity$$rawDType"
                addCriteria(entity.get<String>("dtype") equal dtype)
            }

            if (fileId != null) {
                addCriteria(entity[ActivityEventEntity::fileId] equal fileId)
            }

            if (user != null) {
                addCriteria(entity[ActivityEventEntity::username] equal user)
            }

            if (minTimestamp != null) {
                addCriteria(entity[ActivityEventEntity::timestamp] greaterThanEquals Date(minTimestamp))
            }

            if (maxTimestamp != null) {
                addCriteria(entity[ActivityEventEntity::timestamp] lessThanEquals Date(maxTimestamp))
            }
        }

        return builder ?: literal(false).toPredicate()
    }

    override fun findEvents(
        session: HibernateSession,
        items: Int,
        filter: ActivityEventFilter
    ): List<ActivityEvent> {
        return session
            .criteria<ActivityEventEntity>(
                orderBy = { ordering() },
                predicate = { applyFilter(filter) }
            )
            .also {
                if (filter.offset != null) it.firstResult = filter.offset
                it.maxResults = items
            }
            .list()
            .map { it.toModel() }
    }

    override fun countEvents(session: HibernateSession, filter: ActivityEventFilter): Long {
        return session.createCriteriaBuilder<Long, ActivityEventEntity>().run {
            criteria.select(count(entity))
            criteria.where(applyFilter(filter))
        }.createQuery(session).list().first()
    }

    override fun insertBatch(session: HibernateSession, events: List<ActivityEvent>) {
        events.sortedBy { it.timestamp }.forEachIndexed { index, activityEvent ->
            session.save(ActivityEventEntity.fromEvent(activityEvent))
            if (index % BATCH_SIZE == 0) {
                session.flush()
                session.clear()
            }
        }
    }

    override fun insert(session: HibernateSession, event: ActivityEvent) {
        session.save(ActivityEventEntity.fromEvent(event))
    }

    companion object {
        private const val BATCH_SIZE = 50
    }
}
