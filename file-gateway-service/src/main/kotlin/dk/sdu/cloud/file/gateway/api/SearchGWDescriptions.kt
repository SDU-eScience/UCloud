package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindToSubProperty
import dk.sdu.cloud.filesearch.api.AdvancedSearchRequest
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

data class SimpleSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    override val load: String?
) : WithPaginationRequest, LoadFileResource

/**
 * @see FileSearchDescriptions.advancedSearch
 */
data class AdvancedSearchRequestWithLoad(
    val request: AdvancedSearchRequest,

    override val load: String?
) : LoadFileResource

object SearchGWDescriptions : RESTDescriptions("${FileSearchDescriptions.namespace}.gateway") {
    const val baseContext: String = "/api/file-search"

    val simpleSearch = callDescription<SimpleSearchRequest, Page<StorageFileWithMetadata>, CommonErrorMessage> {
        val delegate = FileSearchDescriptions.simpleSearch
        name = "fileSearchSimple"
        method = delegate.method

        auth {
            access = delegate.auth.access
            roles = delegate.auth.roles
            desiredScope = delegate.auth.desiredScope
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(SimpleSearchRequest::query)
            +boundTo(SimpleSearchRequest::itemsPerPage)
            +boundTo(SimpleSearchRequest::page)
            +boundTo(SimpleSearchRequest::load)
        }
    }

    val advancedSearch =
        callDescription<AdvancedSearchRequestWithLoad, Page<StorageFileWithMetadata>, CommonErrorMessage> {
            val delegate = FileSearchDescriptions.advancedSearch

            name = "fileSearchAdvanced"
            method = delegate.method

            auth {
                access = delegate.auth.access
                roles = delegate.auth.roles
                desiredScope = delegate.auth.desiredScope
            }

            path {
                using(baseContext)
                +"advanced"
            }

            params {
                +boundTo(AdvancedSearchRequestWithLoad::load)
            }

            body { bindToSubProperty(AdvancedSearchRequestWithLoad::request) }
        }
}
