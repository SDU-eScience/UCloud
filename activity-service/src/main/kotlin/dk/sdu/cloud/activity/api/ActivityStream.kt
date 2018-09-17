package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest

data class ActivityStreamFileReference(val id: String)

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
        val file: ActivityStreamFileReference,
        val count: Int,
        override val timestamp: Long
    ) : ActivityStreamEntry<CountedFileActivityOperation>()

    data class Tracked(
        override val operation: TrackedFileActivityOperation,
        val files: List<ActivityStreamFileReference>,
        override val timestamp: Long
    ) : ActivityStreamEntry<TrackedFileActivityOperation>()
}

enum class CountedFileActivityOperation {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    FAVORITE,
    DOWNLOAD
}

enum class TrackedFileActivityOperation {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    CREATE,
    UPDATE,
    DELETE,
    RENAME
}
