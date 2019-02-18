package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
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

object ZenodoDescriptions : CallDescriptionContainer("zenodo") {
    const val baseContext = "/api/zenodo"

    val requestAccess = call<ZenodoAccessRequest, ZenodoAccessRedirectURL, CommonErrorMessage>("requestAccess") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"request"
            }

            params {
                +boundTo(ZenodoAccessRequest::returnTo)
            }
        }
    }

    val publish = call<ZenodoPublishRequest, ZenodoPublishResponse, CommonErrorMessage>("publish") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"publish"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val status = call<Unit, ZenodoConnectedStatus, CommonErrorMessage>("status") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"status"
            }
        }
    }

    val listPublications = call<ZenodoListPublicationsRequest, Page<ZenodoPublication>, CommonErrorMessage>("listPublications") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"publications"
            }

            params {
                +boundTo(ZenodoListPublicationsRequest::itemsPerPage)
                +boundTo(ZenodoListPublicationsRequest::page)
            }
        }
    }

    val findPublicationById = call<FindByLongId, ZenodoPublicationWithFiles, CommonErrorMessage>("findPublicationById") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"publications"
                +boundTo(FindByLongId::id)
            }
        }
    }
}
