package dk.sdu.cloud.storage.api

import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.MappedEventProducer

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
 */
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
     * Internal timestamp
     *
     * Format is milliseconds since unix epoch
     */
    abstract val timestamp: Long

    /**
     * Emitted when a file has been created.
     */
    data class CreatedOrModified(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long,
        val type: FileType
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
     * __Note:__ The path in this case refers to the _new_ path. No effort is made to preserve the old path.
     */
    data class Moved(
        override val id: String,
        override val path: String,
        override val owner: String,
        override val timestamp: Long
    ) : StorageEvent()
}

typealias StorageEventProducer = MappedEventProducer<String, StorageEvent>

object StorageEvents : KafkaDescriptions() {
    /**
     * A list of storage events. Keyed by the file ID
     */
    val events = stream<String, StorageEvent>("storage-events") { it.id }
}