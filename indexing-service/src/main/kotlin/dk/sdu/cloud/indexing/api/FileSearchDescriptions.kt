package dk.sdu.cloud.indexing.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import dk.sdu.cloud.storage.api.FileType
import io.ktor.http.HttpMethod

data class SimpleSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

data class SearchResult(
    val path: String,
    val fileType: FileType
)

object FileSearchDescriptions : RESTDescriptions(IndexingServiceDescription) {
    const val baseContext: String = "/api/file-search"

    val simpleSearch = callDescription<SimpleSearchRequest, Page<SearchResult>, CommonErrorMessage> {
        prettyName = "fileSearchSimple"
        method = HttpMethod.Get

        path {
            using(baseContext)
        }

        params {
            +boundTo(SimpleSearchRequest::query)
            +boundTo(SimpleSearchRequest::itemsPerPage)
            +boundTo(SimpleSearchRequest::page)
        }
    }
}
