package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityStreamEntry
import dk.sdu.cloud.activity.api.ActivityStreamFileReference
import dk.sdu.cloud.activity.api.CountedFileActivityOperation
import dk.sdu.cloud.activity.api.TrackedFileActivityOperation
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Embeddable
data class HActivityStreamKey(
    var id: String?,

    var subjectType: HActivityStreamSubjectType,

    var operation: String = "",

    @Temporal(TemporalType.TIMESTAMP)
    var timestamp: Date = Date(System.currentTimeMillis())
) : Serializable

enum class HActivityStreamSubjectType {
    FILE,
    USER
}

@Entity
@Table(name = "activity_stream_entries")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
sealed class HActivityStreamEntry {
    @get:Id
    @get:EmbeddedId
    abstract var header: HActivityStreamKey

    abstract fun toModel(): ActivityStreamEntry<*>

    @Entity
    class Counted(
        override var header: HActivityStreamKey,

        @get:OneToMany(fetch = FetchType.EAGER)
        var entries: MutableList<HActivityStreamCountedEntry>,

        operation: CountedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }

        override fun toModel(): ActivityStreamEntry<*> {
            return ActivityStreamEntry.Counted(
                CountedFileActivityOperation.valueOf(header.operation),
                entries.map { ActivityStreamEntry.CountedFile(it.fileId, it.count) },
                header.timestamp.time
            )
        }

        override fun toString(): String {
            return "Counted(header=$header, entries=$entries)"
        }
    }

    @Entity
    class Tracked(
        override var header: HActivityStreamKey,

        // It appears, that since we add annotations on the getters (in super class), then this must all add its
        // annotations on the getter (as opposed to the property)
        @get:OneToMany(fetch = FetchType.EAGER)
        var fileIds: MutableSet<HActivityStreamFileReference>,
        operation: TrackedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }

        override fun toModel(): ActivityStreamEntry<*> {
            return ActivityStreamEntry.Tracked(
                TrackedFileActivityOperation.valueOf(header.operation),
                fileIds.map { ActivityStreamFileReference(it.fileId) }.toSet(),
                header.timestamp.time
            )
        }

        override fun toString(): String {
            return "Tracked(header=$header, fileIds=$fileIds)"
        }
    }
}

@Entity
@Table(name = "counted_entries")
data class HActivityStreamCountedEntry(
    var fileId: String,
    var count: Int,

    @Id
    @GeneratedValue
    var id: Long? = null
)

@Entity
@Table(name = "file_references")
data class HActivityStreamFileReference(
    var fileId: String,

    @Id
    @GeneratedValue
    var id: Long? = null
)

class HibernateActivityStreamDao : ActivityStreamDao<HibernateSession> {
    override fun createStreamIfNotExists(session: HibernateSession, stream: ActivityStream) {
        // No-op
        log.debug("creating stream: $stream")
    }

    override fun insertIntoStream(session: HibernateSession, stream: ActivityStream, entry: ActivityStreamEntry<*>) {
        log.debug("Inserting entry into stream: stream = $stream, entry = $entry")
        val earliestTimestampForInsertion = Date(System.currentTimeMillis() - 1000 * 60 * 30)

        val existing = session.criteria<HActivityStreamEntry> {
            val header = entity[HActivityStreamEntry::header]

            (header[HActivityStreamKey::id] equal stream.subject.toId()) and
                    (header[HActivityStreamKey::subjectType] equal stream.subject.toType()) and
                    (header[HActivityStreamKey::operation] equal entry.operation.name) and
                    (header[HActivityStreamKey::timestamp] greaterThan earliestTimestampForInsertion)
        }.list().firstOrNull()

        if (existing == null) {
            log.debug("No existing entry found. Inserting new entry")
            val newEntity = entry.toEntity(session, stream)
            session.save(newEntity)
        } else {
            log.debug("Found an existing entry: $existing")

            @Suppress("unused_variable")
            val ignored: Any? = when (existing) {
                is HActivityStreamEntry.Tracked -> {
                    entry as ActivityStreamEntry.Tracked

                    existing.fileIds.addAll(
                        entry.files.map { ref ->
                            HActivityStreamFileReference(ref.id).also { session.save(it) }
                        }
                    )
                    session.save(existing)
                }

                is HActivityStreamEntry.Counted -> {
                    entry as ActivityStreamEntry.Counted

                    val mappedEntries = existing.entries.associateBy { it.fileId }
                    entry.entries.forEach { counted ->
                        val countedFile = mappedEntries[counted.fileId]
                        if (countedFile != null) {
                            countedFile.count += counted.count
                            session.saveOrUpdate(countedFile)
                        } else {
                            val newEntry = HActivityStreamCountedEntry(counted.fileId, counted.count)
                            existing.entries.add(newEntry)
                            session.save(newEntry)
                        }
                    }

                    session.save(existing)
                }
            }
        }
    }

    private fun ActivityStreamEntry<*>.toEntity(
        session: HibernateSession,
        stream: ActivityStream
    ): HActivityStreamEntry = when (this) {
        is ActivityStreamEntry.Counted ->
            HActivityStreamEntry.Counted(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                entries.map { entry ->
                    HActivityStreamCountedEntry(
                        entry.fileId,
                        entry.count
                    ).also { session.save(it) }
                }.toMutableList(),
                operation
            )

        is ActivityStreamEntry.Tracked ->
            HActivityStreamEntry.Tracked(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                files.map { ref ->
                    HActivityStreamFileReference(ref.id).also { session.save(it) }
                }.toMutableSet(),
                operation
            )
    }

    private fun ActivityStreamSubject.toId(): String = when (this) {
        is ActivityStreamSubject.File -> id
        is ActivityStreamSubject.User -> username
    }

    private fun ActivityStreamSubject.toType(): HActivityStreamSubjectType = when (this) {
        is ActivityStreamSubject.File -> HActivityStreamSubjectType.FILE
        is ActivityStreamSubject.User -> HActivityStreamSubjectType.USER
    }

    override fun loadStream(
        session: HibernateSession,
        stream: ActivityStream,
        pagination: NormalizedPaginationRequest
    ): Page<ActivityStreamEntry<*>> {
        log.debug("Loading stream $stream with pagination $pagination")

        return session.paginatedCriteria<HActivityStreamEntry>(
            pagination,

            orderBy = {
                listOf(descinding(entity[HActivityStreamEntry::header][HActivityStreamKey::timestamp]))
            },

            predicate = {
                val header = entity[HActivityStreamEntry::header]

                (header[HActivityStreamKey::id] equal stream.subject.toId()) and
                        (header[HActivityStreamKey::subjectType] equal stream.subject.toType())
            }
        ).mapItems { it.toModel() }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
