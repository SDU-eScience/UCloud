package dk.sdu.cloud.file.favorite.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
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
    var newStatus: Boolean? = null
)

data class FavoriteStatusRequest(
    val files: List<String>
)

/**
 * Contains a mapping between [StorageFile.pathOrNull] and their favorite status
 */
data class FavoriteStatusResponse(
    val favorited: Map<String, Boolean>
)
typealias ListRequest = PaginationRequest

typealias ListResponse = Page<StorageFile>

object FileFavoriteDescriptions : CallDescriptionContainer("${FileDescriptions.namespace}.favorite") {
    val baseContext = "/api/files/favorite"

    val toggleFavorite =
        call<ToggleFavoriteRequest, ToggleFavoriteResponse, CommonErrorMessage>("toggleFavorite") {
            audit<ToggleFavoriteAudit>()
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"toggle"
                }

                params {
                    +boundTo(ToggleFavoriteRequest::path)
                }
            }
        }

    val favoriteStatus = call<FavoriteStatusRequest, FavoriteStatusResponse, CommonErrorMessage>("favoriteStatus.2") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"status"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val list = call<ListRequest, ListResponse, CommonErrorMessage>("list.2") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list"
            }

            params {
                +boundTo(ListRequest::itemsPerPage)
                +boundTo(ListRequest::page)
            }
        }
    }
}
