package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

data class StreamByPathRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

typealias StreamByPathResponse = Page<ActivityStreamEntry<*>>

data class StreamForUserRequest(
    val user: String?,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

typealias StreamForUserResponse = Page<ActivityStreamEntry<*>>

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(ActivityStreamEntry.Counted::class, name = "counted"),
    JsonSubTypes.Type(ActivityStreamEntry.Tracked::class, name = "tracked")
)
sealed class ActivityStreamEntry<OperationType : Enum<OperationType>> {
    abstract val timestamp: Long
    abstract val operation: OperationType

    data class Counted(
        override val operation: CountedFileActivityOperation,
        val files: Set<StreamFileReference.WithOpCount>,
        override val timestamp: Long
    ) : ActivityStreamEntry<CountedFileActivityOperation>()


    data class Tracked(
        override val operation: TrackedFileActivityOperation,
        val files: Set<StreamFileReference.Basic>,
        override val timestamp: Long
    ) : ActivityStreamEntry<TrackedFileActivityOperation>()
}

/**
 * A reference to a file in the context of an [ActivityStreamEntry]
 *
 * All references contain an [id] and optionally a [path]. Additionally it may contain more attributes. For example,
 * [WithOpCount] also contains a count of how many times a given operation is used. This is used in
 * [ActivityStreamEntry.Counted].
 */
sealed class StreamFileReference {
    abstract val id: String
    abstract val path: String?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StreamFileReference

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    // Sub-classes
    class Basic(override val id: String, override val path: String?) : StreamFileReference() {
        override fun toString(): String = "Basic(id='$id', path=$path)"

        fun withPath(path: String?): Basic = Basic(id, path)
    }

    class WithOpCount(
        override val id: String,
        override val path: String?,
        val count: Int
    ) : StreamFileReference() {
        override fun toString(): String = "Counted(id='$id', path=$path, count=$count)"

        fun withPath(path: String?): WithOpCount = WithOpCount(id, path, count)
    }
}

enum class CountedFileActivityOperation {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    FAVORITE,
    DOWNLOAD;

    companion object {
        fun fromEventOrNull(event: ActivityEvent): CountedFileActivityOperation? = when (event) {
            is ActivityEvent.Favorite -> FAVORITE
            is ActivityEvent.Download -> DOWNLOAD
            else -> null
        }
    }
}

enum class TrackedFileActivityOperation {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    CREATE,
    UPDATE,
    DELETE,
    MOVED;

    companion object {
        fun fromEventOrNull(event: ActivityEvent): TrackedFileActivityOperation? = when (event) {
            is ActivityEvent.Updated -> UPDATE
            is ActivityEvent.Moved -> MOVED
            else -> null
        }
    }
}
