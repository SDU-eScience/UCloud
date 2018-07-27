package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.MappedEventProducer
import org.apache.kafka.streams.kstream.KStream

/**
 * Represents an event which has occurred inside of the storage system
 *
 * Each file has a unique identifier (implementation dependant, for CephFS this is the inode). Additionally every
 * event contains the canonical path. SDUCloud doesn't support hard-links. As a result we can be certain that each
 * file has exactly one canonical path at any point in time.
 *
 * The effects of each event is guaranteed to have taken place internally in the system when they are emitted.
 *
 * __Note:__ Since events are emitted and consumed asynchronously you cannot be certain that the file is present at
 * the [StorageEvent.path] or that the file even exists, since multiple new events may have occurred when the event
 * is consumed.
 *
 * __Note:__ The events are emitted on a best-effort basis. It is entirely possible that these events can be out-of-sync
 * with the real system. As a result clients should be able to handle event sequences that are technically
 * impossible to occur. For example, a create event might be missed and jump straight to a moved or deleted event.
 *
 * __Note:__ Regular scans are performed to ensure the events and FS are in-sync. When inconsistencies are detected new
 * events will be emitted to make the events consistent with the FS.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StorageEvent.CreatedOrRefreshed::class, name = "created"),
    JsonSubTypes.Type(value = StorageEvent.Deleted::class, name = "deleted"),
    JsonSubTypes.Type(value = StorageEvent.AnnotationsUpdated::class, name = "annotations"),
    JsonSubTypes.Type(value = StorageEvent.SensitivityUpdated::class, name = "sensitivity"),
    JsonSubTypes.Type(value = StorageEvent.Invalidated::class, name = "invalidated")
)
sealed class StorageEvent {
    /**
     * The unique ID of the file
     *
     * The ID is guaranteed to be unique for an entire file system. Across (potential) federation it is not guaranteed
     * to be unique.
     *
     * The ID is an opaque identifier, and its contents is entirely implementation dependant. For the CephFS
     * implementation this identifier corresponds to the inode of the file.
     */
    abstract val id: String

    /**
     * The canonical path of the file
     *
     * Because SDUCloud doesn't support hard links we are guaranteed that each file has exactly one canonical path.
     */
    abstract val path: String

    /**
     * The SDUCloud username of the owner of this file
     */
    abstract val owner: String

    /**
     * Internal timestamp for when the event occurred
     *
     * Format is milliseconds since unix epoch
     *
     * __Note:__ This is different from the time the event is emitted. In case of events emitted due to an
     * out-of-sync event stream the timestamps may differ significantly.
     *
     * __Note:__ These timestamps are best-effort.
     */
    abstract val timestamp: Long

    /**
     * Emitted when a file has been created or a full-refresh of the file is deemed necessary.
     *
     * It is safe for clients to overwrite their previous entry at indexed by [id]. None of the old attributes are
     * guaranteed to be the same. For example, it is perfectly valid for the system not to emit a [Moved] event and just
     * send a new [CreatedOrRefreshed] event with a new [path]. This will, for example, occur when inconsistencies are
     * detected.
     */
    data class CreatedOrRefreshed(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long,
        val fileType: FileType,

        val fileTimestamps: Timestamps,
        val size: Long,
        val checksum: FileChecksum,

        val isLink: Boolean,
        val linkTarget: String?,
        val linkTargetId: String?,

        val annotations: Set<String>,

        val sensitivityLevel: SensitivityLevel
    ) : StorageEvent()

    /**
     * Emitted when the sensitivity level of a file has changed
     */
    data class SensitivityUpdated(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long,

        val sensitivityLevel: SensitivityLevel
    ) : StorageEvent()

    /**
     * Emitted when the annotations of a file has changed.
     *
     * A complete refresh of all annotations are sent, not just new/deleted ones.
     */
    data class AnnotationsUpdated(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long,

        val annotations: Set<String>
    ) : StorageEvent()

    /**
     * Emitted when a file has been deleted
     */
    data class Deleted(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long
    ) : StorageEvent()

    /**
     * Emitted when a file is moved from one location to another
     *
     * __Note:__ The path in this case refers to the _new_ path.
     */
    data class Moved(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long,
        val oldPath: String
    ) : StorageEvent()

    /**
     * Indicates that the events have been out-of-sync with the FS for all children of [path]
     *
     * Clients should invalidate their caches for all paths starting with [path] (including [path]).
     *
     * __Note:__ The [id], [owner] and [timestamp] are all best-effort approximates and should not be relied upon
     *
     * __Note:__ The [id] should _never_ be relied upon for this event time.
     */
    data class Invalidated(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long
    ) : StorageEvent()
}

data class Timestamps(val accessed: Long, val created: Long, val modified: Long)
data class FileChecksum(val algorithm: String, val checksum: String)

/**
 * Represents a [StorageFile] materialized from a stream of [StorageEvent]s.
 */
data class EventMaterializedStorageFile(
    val id: String,
    val path: String,
    val owner: String,
    val fileType: FileType,

    val fileTimestamps: Timestamps,
    val size: Long,
    val checksum: FileChecksum,

    val isLink: Boolean,
    val linkTarget: String?,
    val linkTargetId: String?,

    val annotations: Set<String>,

    val sensitivityLevel: SensitivityLevel
)

typealias StorageEventProducer = MappedEventProducer<String, StorageEvent>
typealias StoraveEventStream = KStream<String, StorageEvent>

object StorageEvents : KafkaDescriptions() {
    /**
     * A list of storage events. Keyed by the file ID
     */
    val events = stream<String, StorageEvent>("storage-events") { it.id }
}