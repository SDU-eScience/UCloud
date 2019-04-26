package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

data class ListRequest(
    override val itemsPerPage: Int?,
    override val page: Int?,
    override val attributes: String?
) : WithPaginationRequest, LoadFileResource

fun ListRequest(
    itemsPerPage: Int?,
    page: Int?,
    load: Set<FileResource>
): ListRequest = ListRequest(itemsPerPage, page, fileResourcesToString(load))

typealias ListResponse = Page<StorageFileWithMetadata>

object FavoriteGWDescriptions : CallDescriptionContainer("${FileFavoriteDescriptions.namespace}.gateway") {
    val baseContext = "/api/files/favorite"

    val list = call<ListRequest, ListResponse, CommonErrorMessage>("list") {
        val delegate = FileFavoriteDescriptions.list

        auth {
            access = delegate.authDescription.access
            roles = delegate.authDescription.roles
            requiredScope = delegate.requiredAuthScope
        }

        http {
            method = delegate.http.method
            path {
                using(baseContext)
            }

            params {
                +boundTo(ListRequest::itemsPerPage)
                +boundTo(ListRequest::page)
                +boundTo(ListRequest::attributes)
            }
        }
    }
}
