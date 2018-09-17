package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ActivityEvent.Download::class, name = "download"),
    JsonSubTypes.Type(value = ActivityEvent.Updated::class, name = "updated"),
    JsonSubTypes.Type(value = ActivityEvent.Favorite::class, name = "favorite"),
    JsonSubTypes.Type(value = ActivityEvent.Inspected::class, name = "inspected"),
    JsonSubTypes.Type(value = ActivityEvent.Renamed::class, name = "renamed")
)
sealed class ActivityEvent {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    abstract val timestamp: Long
    abstract val fileId: String
    abstract val username: String

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

    data class Renamed(
        override val username: String,
        val newName: String,
        override val timestamp: Long,
        override val fileId: String
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
