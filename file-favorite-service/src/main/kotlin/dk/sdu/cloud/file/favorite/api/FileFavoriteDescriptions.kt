package dk.sdu.cloud.file.favorite.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.client.request.header
import io.ktor.http.HttpMethod

data class ToggleFavoriteRequest(
    val path: String
)

data class ToggleFavoriteResponse(
    val failures: List<String>
)

data class ToggleFavoriteAudit(
    val files: List<ToggleFavoriteFileAudit>
)

data class ToggleFavoriteFileAudit(
    val path: String,
    var fileId: String? = null,
    var newStatus: Boolean? = null
)

data class FavoriteStatusRequest(
    val files: List<StorageFile>
)

/**
 * Contains a mapping between [StorageFile.fileId] and their [FavoriteStatus]
 */
data class FavoriteStatusResponse(
    val favorited: Map<String, Boolean>
)
typealias ListRequest = PaginationRequest

typealias ListResponse = Page<StorageFile>

object FileFavoriteDescriptions : RESTDescriptions("${FileDescriptions.namespace}.favorite") {
    val baseContext = "/api/files/favorite"

    internal val toggleFavoriteDelete =
        callDescriptionWithAudit<ToggleFavoriteRequest, ToggleFavoriteResponse, CommonErrorMessage, ToggleFavoriteAudit> {
            name = "toggleFavorite"
            method = HttpMethod.Delete

            auth {
                access = AccessRight.READ_WRITE
            }

            path {
                using(baseContext)
            }

            params {
                +boundTo(ToggleFavoriteRequest::path)
            }
        }

    val toggleFavorite =
        callDescriptionWithAudit<ToggleFavoriteRequest, ToggleFavoriteResponse, CommonErrorMessage, ToggleFavoriteAudit> {
            name = "toggleFavorite"
            method = HttpMethod.Post

            auth {
                access = AccessRight.READ_WRITE
            }

            path {
                using(baseContext)
            }

            params {
                +boundTo(ToggleFavoriteRequest::path)
            }
        }

    val favoriteStatus = callDescription<FavoriteStatusRequest, FavoriteStatusResponse, CommonErrorMessage> {
        name = "favoriteStatus"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"status"
        }

        body { bindEntireRequestFromBody() }
    }

    val list = callDescription<ListRequest, ListResponse, CommonErrorMessage>(
        additionalRequestConfiguration = { header("x-no-load", "true") }
    ) {
        name = "list"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListRequest::itemsPerPage)
            +boundTo(ListRequest::page)
        }
    }
}
