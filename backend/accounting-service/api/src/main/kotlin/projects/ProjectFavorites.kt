package dk.sdu.cloud.project.favorite.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Page
import dk.sdu.cloud.WithPaginationRequest
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ListFavoritesRequest(
    override val itemsPerPage: Int? = 10,
    override val page: Int? = 0
): WithPaginationRequest

typealias ListFavoritesResponse = Page<String>

@Serializable
data class ToggleFavoriteRequest(
    val projectId: String
)

typealias ToggleFavoriteResponse = Unit

object ProjectFavorites : CallDescriptionContainer("project.favorite") {
    const val baseContext = "/api/projects/favorite"

    init {
        description = "API to handle favorite status of a Project.\n\n" + ApiConventions.nonConformingApiWarning
    }

    val toggleFavorite = call("toggleFavorite", ToggleFavoriteRequest.serializer(), ToggleFavoriteResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
