package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class ZenodoAccessRequest(val returnTo: String)
data class ZenodoAccessRedirectURL(val redirectTo: String)

data class ZenodoPublishRequest(val name: String, val filePaths: List<String>)
data class ZenodoPublishResponse(val publicationId: Long)

data class ZenodoConnectedStatus(val connected: Boolean)

enum class ZenodoPublicationStatus {
    PENDING,
    UPLOADING,
    COMPLETE,
    FAILURE
}

data class ZenodoPublication(
    val id: Long,
    val name: String,
    val status: ZenodoPublicationStatus,
    val zenodoAction: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val uploads: List<ZenodoUpload>
)

data class ZenodoUpload(
    val dataObject: String,
    val hasBeenTransmitted: Boolean,
    val updatedAt: Long
)

typealias ZenodoPublicationWithFiles = ZenodoPublication

data class ZenodoListPublicationsRequest(
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

object ZenodoDescriptions : RESTDescriptions("zenodo") {
    const val baseContext = "/api/zenodo"

    val requestAccess = callDescription<ZenodoAccessRequest, ZenodoAccessRedirectURL, CommonErrorMessage> {
        method = HttpMethod.Post
        prettyName = "requestAccess"

        path {
            using(baseContext)
            +"request"
        }

        params {
            +boundTo(ZenodoAccessRequest::returnTo)
        }
    }

    val publish = callDescription<ZenodoPublishRequest, ZenodoPublishResponse, CommonErrorMessage> {
        method = HttpMethod.Post
        prettyName = "publish"

        path {
            using(baseContext)
            +"publish"
        }

        body { bindEntireRequestFromBody() }
    }

    val status = callDescription<Unit, ZenodoConnectedStatus, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "status"

        path {
            using(baseContext)
            +"status"
        }
    }

    val listPublications = callDescription<ZenodoListPublicationsRequest, Page<ZenodoPublication>, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "listPublications"

        path {
            using(baseContext)
            +"publications"
        }

        params {
            +boundTo(ZenodoListPublicationsRequest::itemsPerPage)
            +boundTo(ZenodoListPublicationsRequest::page)
        }
    }

    val findPublicationById = callDescription<FindByLongId, ZenodoPublicationWithFiles, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "findPublicationById"

        path {
            using(baseContext)
            +"publications"
            +boundTo(FindByLongId::id)
        }
    }
}