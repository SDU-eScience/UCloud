package dk.sdu.cloud.file.favorite.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Page
import dk.sdu.cloud.PaginationRequest
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StorageFile
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class ToggleFavoriteRequest(
    val path: String
)

@Serializable
data class ToggleFavoriteResponse(
    val failures: List<String>
)

@Serializable
data class ToggleFavoriteAudit(
    val files: List<ToggleFavoriteFileAudit>
)

@Serializable
data class ToggleFavoriteFileAudit(
    val path: String,
    var newStatus: Boolean? = null
)

@Serializable
data class FavoriteStatusRequest(
    val files: List<String>
)

/**
 * Contains a mapping between [StorageFile.pathOrNull] and their favorite status
 */
@Serializable
data class FavoriteStatusResponse(
    val favorited: Map<String, Boolean>
)
typealias ListRequest = PaginationRequest

typealias ListResponse = Page<StorageFile>

@TSTopLevel
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
