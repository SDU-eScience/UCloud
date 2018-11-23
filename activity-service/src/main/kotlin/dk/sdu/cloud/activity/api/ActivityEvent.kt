package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.WithPaginationRequest

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ActivityEvent.Download::class, name = "download"),
    JsonSubTypes.Type(value = ActivityEvent.Updated::class, name = "updated"),
    JsonSubTypes.Type(value = ActivityEvent.Favorite::class, name = "favorite"),
    JsonSubTypes.Type(value = ActivityEvent.Inspected::class, name = "inspected"),
    JsonSubTypes.Type(value = ActivityEvent.Moved::class, name = "moved")
)
sealed class ActivityEvent {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    // When adding new entries here, you will also need to add entries in:
    // ActivityEventDao, ActivityStream and ActivityStreamDao

    abstract val timestamp: Long
    abstract val fileId: String
    abstract val username: String

    // TODO We cannot reliably track who uploaded a file (due to bulk uploads)

    data class Download(
        override val username: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Updated(
        override val username: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Favorite(
        override val username: String,
        val isFavorite: Boolean,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Inspected(
        override val username: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Moved(
        override val username: String,
        val newName: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Deleted(
        override val timestamp: Long,
        override val fileId: String,
        override val username: String
    ) : ActivityEvent()
}

data class ListActivityByIdRequest(
    val id: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListActivityByIdResponse = Page<ActivityEvent>


data class ListActivityByPathRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListActivityByPathResponse = Page<ActivityEvent>
