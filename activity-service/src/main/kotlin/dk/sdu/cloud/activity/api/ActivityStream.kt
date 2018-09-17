package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest

data class ActivityStreamFileReference(val path: String, val id: String)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(ActivityStreamEntry.Created::class, name = "created"),
    JsonSubTypes.Type(ActivityStreamEntry.Downloaded::class, name = "downloaded"),
    JsonSubTypes.Type(ActivityStreamEntry.Favorite::class, name = "favorite"),
    JsonSubTypes.Type(ActivityStreamEntry.Renamed::class, name = "renamed"),
    JsonSubTypes.Type(ActivityStreamEntry.Deleted::class, name = "deleted"),
    JsonSubTypes.Type(ActivityStreamEntry.Updated::class, name = "updated")
)
sealed class ActivityStreamEntry {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity
    abstract val timestamp: Long

    data class Created(
        val files: List<ActivityStreamFileReference>,
        override val timestamp: Long
    ) : ActivityStreamEntry()

    data class Updated(
        val files: List<ActivityStreamFileReference>,
        override val timestamp: Long
    ) : ActivityStreamEntry()

    data class Deleted(
        val files: List<ActivityStreamFileReference>,
        override val timestamp: Long
    ) : ActivityStreamEntry()

    data class Downloaded(
        val file: ActivityStreamFileReference,
        val count: Int,
        override val timestamp: Long
    ) : ActivityStreamEntry()

    data class Favorite(
        val file: ActivityStreamFileReference,
        val count: Int,
        override val timestamp: Long
    ) : ActivityStreamEntry()

    data class Renamed(
        val fileId: String,
        val from: String,
        val to: String,
        override val timestamp: Long
    ) : ActivityStreamEntry()
}
