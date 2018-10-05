package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.*
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
import javax.persistence.criteria.Expression
import javax.persistence.criteria.Predicate
import kotlin.collections.HashSet

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
        operation: CountedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }

        @get:OneToMany(mappedBy = "entry")
        var files: MutableSet<HActivityStreamCountedEntry> = HashSet()

        @get:OneToMany(mappedBy = "entry")
        var users: MutableSet<HActivityStreamParticipatingUser> = HashSet()

        override fun toModel(): ActivityStreamEntry<*> {
            return ActivityStreamEntry.Counted(
                CountedFileActivityOperation.valueOf(header.operation),
                header.timestamp.time,
                files.asSequence().map { StreamFileReference.WithOpCount(it.fileId, null, it.count) }.toSet(),
                users.asSequence().map { UserReference(it.username) }.toSet()
            )
        }

        override fun toString(): String {
            return "Counted(header=$header, files=$files)"
        }
    }

    @Entity
    class Tracked(
        override var header: HActivityStreamKey,
        operation: TrackedFileActivityOperation
    ) : HActivityStreamEntry() {
        init {
            header.operation = operation.name
        }

        // It appears, that since we add annotations on the getters (in super class), then this must all add its
        // annotations on the getter (as opposed to the property)
        @get:OneToMany(mappedBy = "entry")
        var fileIds: MutableSet<HActivityStreamFileReference> = HashSet()

        @get:OneToMany(mappedBy = "entry")
        var users: MutableSet<HActivityStreamParticipatingUser> = HashSet()

        override fun toModel(): ActivityStreamEntry<*> {
            return ActivityStreamEntry.Tracked(
                TrackedFileActivityOperation.valueOf(header.operation),
                header.timestamp.time,
                fileIds.asSequence().map { StreamFileReference.Basic(it.fileId, null) }.toSet(),
                users.asSequence().map { UserReference(it.username) }.toSet()
            )
        }

        override fun toString(): String {
            return "Tracked(header=$header, fileIds=$fileIds)"
        }
    }
}

@Entity
@Table(name = "entry_users")
data class HActivityStreamParticipatingUser(
    var username: String,

    @ManyToOne
    var entry: HActivityStreamEntry,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    override fun toString(): String {
        return "HActivityStreamParticipatingUser(username='$username', id=$id)"
    }
}

@Entity
@Table(name = "counted_entries")
data class HActivityStreamCountedEntry(
    var fileId: String,
    var count: Int,

    @ManyToOne
    var entry: HActivityStreamEntry,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    override fun toString(): String {
        return "HActivityStreamCountedEntry(fileId='$fileId', count=$count, id=$id)"
    }
}

@Entity
@Table(name = "file_references")
data class HActivityStreamFileReference(
    var fileId: String,

    @ManyToOne
    var entry: HActivityStreamEntry,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    override fun toString(): String {
        return "HActivityStreamFileReference(fileId='$fileId', id=$id)"
    }
}

class HibernateActivityStreamDao : ActivityStreamDao<HibernateSession> {
    override fun createStreamIfNotExists(session: HibernateSession, stream: ActivityStream) {
        // No-op
        log.debug("creating stream: $stream")
    }

    override fun insertIntoStream(session: HibernateSession, stream: ActivityStream, entry: ActivityStreamEntry<*>) {
        log.debug("Inserting entry into stream: stream = $stream, entry = $entry")
        val earliestTimestampForInsertion = Date(entry.timestamp - 1000 * 60 * 30)

        val existing = session.criteria<HActivityStreamEntry> {
            val header = entity[HActivityStreamEntry::header]

            (header[HActivityStreamKey::id] equal stream.subject.toId()) and
                    (header[HActivityStreamKey::subjectType] equal stream.subject.toType()) and
                    (header[HActivityStreamKey::operation] equal entry.operation.name) and
                    (builder.greaterThanOrEqualTo(header[HActivityStreamKey::timestamp], earliestTimestampForInsertion))
            // TODO We are missing a greaterThanOrEqualsTo call in criteria DSL
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

                    existing.users.addAll(entry.users.map { ref ->
                        HActivityStreamParticipatingUser(ref.username, existing).also { session.save(it) }
                    })

                    existing.fileIds.addAll(
                        entry.files.map { ref ->
                            HActivityStreamFileReference(ref.id, existing).also { session.save(it) }
                        }
                    )
                    session.save(existing)
                }

                is HActivityStreamEntry.Counted -> {
                    entry as ActivityStreamEntry.Counted

                    existing.users.addAll(entry.users.map { ref ->
                        HActivityStreamParticipatingUser(ref.username, existing).also { session.save(it) }
                    })

                    val mappedEntries = existing.files.associateBy { it.fileId }
                    entry.files.forEach { counted ->
                        val countedFile = mappedEntries[counted.id]
                        if (countedFile != null) {
                            countedFile.count += counted.count
                            session.saveOrUpdate(countedFile)
                        } else {
                            val newEntry = HActivityStreamCountedEntry(counted.id, counted.count, existing)
                            existing.files.add(newEntry)
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
        is ActivityStreamEntry.Counted -> {
            val counted = HActivityStreamEntry.Counted(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                operation
            )

            counted.files = files
                .asSequence()
                .map { entry ->
                    HActivityStreamCountedEntry(
                        entry.id,
                        entry.count,
                        counted
                    ).also { session.save(it) }
                }
                .toMutableSet()

            counted.users = users
                .asSequence()
                .map { userRef ->
                    HActivityStreamParticipatingUser(userRef.username, counted).also { session.save(it) }
                }
                .toMutableSet()

            counted
        }

        is ActivityStreamEntry.Tracked -> {
            val tracked = HActivityStreamEntry.Tracked(
                HActivityStreamKey(
                    stream.subject.toId(),
                    stream.subject.toType(),
                    timestamp = Date(timestamp)
                ),
                operation
            )

            tracked.fileIds = files
                .asSequence()
                .map { ref ->
                    HActivityStreamFileReference(ref.id, tracked).also { session.save(it) }
                }
                .toMutableSet()

            tracked.users = users
                .asSequence()
                .map { ref ->
                    HActivityStreamParticipatingUser(ref.username, tracked).also { session.save(it) }
                }
                .toMutableSet()

            tracked
        }
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
