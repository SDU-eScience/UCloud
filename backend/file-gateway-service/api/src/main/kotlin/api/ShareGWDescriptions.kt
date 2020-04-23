package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import dk.sdu.cloud.share.api.Shares

object ShareGWDescriptions : CallDescriptionContainer("${Shares.namespace}.gateway") {
    val baseContext = Shares.baseContext

    val listFiles = call<ListFiles.Request, Page<StorageFileWithMetadata>, CommonErrorMessage>("listFiles") {
        val delegate = Shares.listFiles

        auth {
            access = delegate.authDescription.access
            roles = delegate.authDescription.roles
            requiredScope = delegate.requiredAuthScope
        }

        http {
            method = delegate.http.method

            path {
                using(baseContext)
                +"list-files"
            }

            params {
                +boundTo(ListFiles.Request::itemsPerPage)
                +boundTo(ListFiles.Request::page)
                +boundTo(ListFiles.Request::attributes)
            }
        }
    }

    object ListFiles {
        data class Request(
            override val itemsPerPage: Int?,
            override val page: Int?,
            override val attributes: String?
        ) : WithPaginationRequest, LoadFileResource
    }
}
