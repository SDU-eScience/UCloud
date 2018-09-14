package dk.sdu.cloud.activity.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
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
    abstract val timestamp: Long
    abstract val fileId: String

    data class Download(
        val username: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Updated(
        val author: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Favorite(
        val username: String,
        val isFavorite: Boolean,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Inspected(
        val username: String,
        override val timestamp: Long,
        override val fileId: String
    ) : ActivityEvent()

    data class Renamed(
        val username: String,
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

object ActivityDescriptions : RESTDescriptions("activity") {
    val baseContext = "/api/activity"

    val listByFileId = callDescription<ListActivityByIdRequest, ListActivityByIdResponse, CommonErrorMessage> {
        name = "listByFileId"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"by-id"
            +boundTo(ListActivityByIdRequest::id)
        }

        params {
            +boundTo(ListActivityByIdRequest::itemsPerPage)
            +boundTo(ListActivityByIdRequest::page)
        }
    }

    val listByPath = callDescription<ListActivityByPathRequest, ListActivityByPathResponse, CommonErrorMessage> {
        name = "listByPath"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"by-path"
        }

        params {
            +boundTo(ListActivityByPathRequest::itemsPerPage)
            +boundTo(ListActivityByPathRequest::page)
            +boundTo(ListActivityByPathRequest::path)
        }
    }
}