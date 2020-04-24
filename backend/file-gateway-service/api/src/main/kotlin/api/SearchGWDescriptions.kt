package dk.sdu.cloud.file.gateway.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.authDescription
import dk.sdu.cloud.calls.bindToSubProperty
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.filesearch.api.AdvancedSearchRequest
import dk.sdu.cloud.filesearch.api.FileSearchDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest

data class SimpleSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    override val attributes: String?
) : WithPaginationRequest, LoadFileResource

/**
 * @see FileSearchDescriptions.advancedSearch
 */
data class AdvancedSearchRequestWithLoad(
    val request: AdvancedSearchRequest,

    override val attributes: String?
) : LoadFileResource

object SearchGWDescriptions : CallDescriptionContainer("${FileSearchDescriptions.namespace}.gateway") {
    const val baseContext: String = "/api/file-search"

    val simpleSearch = call<SimpleSearchRequest, Page<StorageFileWithMetadata>, CommonErrorMessage>("simpleSearch") {
        val delegate = FileSearchDescriptions.simpleSearch
        auth {
            access = delegate.authDescription.access
            roles = delegate.authDescription.roles
            requiredScope = delegate.authDescription.requiredScope
        }

        http {
            method = delegate.http.method

            path {
                using(baseContext)
            }

            params {
                +boundTo(SimpleSearchRequest::query)
                +boundTo(SimpleSearchRequest::itemsPerPage)
                +boundTo(SimpleSearchRequest::page)
                +boundTo(SimpleSearchRequest::attributes)
            }
        }
    }

    val advancedSearch =
        call<AdvancedSearchRequestWithLoad, Page<StorageFileWithMetadata>, CommonErrorMessage>("advancedSearch") {
            val delegate = FileSearchDescriptions.advancedSearch
            auth {
                access = delegate.authDescription.access
                roles = delegate.authDescription.roles
                requiredScope = delegate.authDescription.requiredScope
            }

            http {
                method = delegate.http.method

                path {
                    using(baseContext)
                    +"advanced"
                }

                params {
                    +boundTo(AdvancedSearchRequestWithLoad::attributes)
                }

                body { bindToSubProperty(AdvancedSearchRequestWithLoad::request) }
            }
        }
}
