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
        val entries: List<CountedFile>,
        override val timestamp: Long
    ) : ActivityStreamEntry<CountedFileActivityOperation>()

    data class CountedFile(val fileId: String, val count: Int)

    data class Tracked(
        override val operation: TrackedFileActivityOperation,
        val files: Set<String>,
        override val timestamp: Long
    ) : ActivityStreamEntry<TrackedFileActivityOperation>()
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
