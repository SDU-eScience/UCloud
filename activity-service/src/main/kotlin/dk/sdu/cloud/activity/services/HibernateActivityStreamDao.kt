package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityStreamEntry
import dk.sdu.cloud.activity.api.CountedFileActivityOperation
import dk.sdu.cloud.activity.api.TrackedFileActivityOperation
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
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

    @Entity
    class Counted(
        override var header: HActivityStreamKey,
        var count: Int,
        operation: CountedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }
    }

    @Entity
    class Tracked(
        override var header: HActivityStreamKey,

        // It appears, that since we add annotations on the getters (in super class), then this must all add its
        // annotations on the getter (as opposed to the property)
        @get:OneToMany
        var fileIds: MutableList<HActivityStreamFileReference>,
        operation: TrackedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }
    }
}

@Entity
@Table(name = "file_references")
class HActivityStreamFileReference(
    var fileId: String,

    @Id
    @GeneratedValue
    var id: Long? = null
)

class HibernateActivityStreamDao : ActivityStreamDao<HibernateSession> {
    override fun createStreamIfNotExists(session: HibernateSession, stream: ActivityStream) {
        // No-op
    }

    override fun insertIntoStream(session: HibernateSession, stream: ActivityStream, entry: ActivityStreamEntry<*>) {
        val earliestTimestampForInsertion = Date(System.currentTimeMillis() - 1000 * 60 * 30)

        val existing = session.criteria<HActivityStreamEntry> {
            val header = entity[HActivityStreamEntry::header]

            (header[HActivityStreamKey::id] equal stream.subject.toId()) and
                    (header[HActivityStreamKey::subjectType] equal stream.subject.toType()) and
                    (header[HActivityStreamKey::operation] equal entry.operation.name) and
                    (header[HActivityStreamKey::timestamp] greaterThan earliestTimestampForInsertion)
        }.list().firstOrNull()

        if (existing == null) {
            val newEntity = entry.toEntity(stream)
            session.save(newEntity)
        } else {
            @Suppress("unused_variable")
            val ignored: Any? = when (existing) {
                is HActivityStreamEntry.Tracked -> {
                    entry as ActivityStreamEntry.Tracked

                    existing.fileIds.addAll(entry.files.map { HActivityStreamFileReference(it.id) })
                    session.save(existing)
                }

                is HActivityStreamEntry.Counted -> {
                    entry as ActivityStreamEntry.Counted

                    existing.count += entry.count
                    session.save(existing)
                }
            }
        }
    }

    private fun ActivityStreamEntry<*>.toEntity(stream: ActivityStream): HActivityStreamEntry = when (this) {
        is ActivityStreamEntry.Counted ->
            HActivityStreamEntry.Counted(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                count,
                operation
            )

        is ActivityStreamEntry.Tracked ->
            HActivityStreamEntry.Tracked(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                files.map { HActivityStreamFileReference(it.id) }.toMutableList(),
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
}
