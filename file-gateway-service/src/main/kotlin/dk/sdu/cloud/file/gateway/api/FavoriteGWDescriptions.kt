package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

data class ListRequest(
    override val itemsPerPage: Int?,
    override val page: Int?,
    override val load: String?
) : WithPaginationRequest, LoadFileResource

fun ListRequest(
    itemsPerPage: Int?,
    page: Int?,
    load: Set<FileResource>
): ListRequest = ListRequest(itemsPerPage, page, fileResourcesToString(load))

typealias ListResponse = Page<StorageFileWithMetadata>

object FavoriteGWDescriptions : RESTDescriptions("${FileFavoriteDescriptions.namespace}.gateway") {
    val baseContext = "/api/files/favorite"

    val list = callDescription<ListRequest, ListResponse, CommonErrorMessage> {
        val delegate = FileFavoriteDescriptions.list

        name = "list"
        method = delegate.method

        auth {
            access = delegate.auth.access
            roles = delegate.auth.roles
            desiredScope = delegate.requiredAuthScope
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListRequest::itemsPerPage)
            +boundTo(ListRequest::page)
            +boundTo(ListRequest::load)
        }
    }
}
