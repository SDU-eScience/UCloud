package dk.sdu.cloud.filesearch.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import io.ktor.http.HttpMethod

data class SimpleSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

data class AdvancedSearchRequest(
    val fileName: String?,
    val extensions: List<String>?,
    val fileTypes: List<FileType>?,
    val createdAt: TimestampQuery?,
    val modifiedAt: TimestampQuery?,
    val sensitivity: List<SensitivityLevel>?,
    val annotations: List<String>?,

    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

data class SearchResult(
    val path: String,
    val fileType: FileType
)

object FileSearchDescriptions : RESTDescriptions("fileSearch") {
    const val baseContext: String = "/api/file-search"

    val simpleSearch = callDescription<SimpleSearchRequest, Page<SearchResult>, CommonErrorMessage> {
        name = "fileSearchSimple"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(SimpleSearchRequest::query)
            +boundTo(SimpleSearchRequest::itemsPerPage)
            +boundTo(SimpleSearchRequest::page)
        }
    }

    val advancedSearch = callDescription<AdvancedSearchRequest, Page<SearchResult>, CommonErrorMessage> {
        name = "fileSearchAdvanced"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"advanced"
        }

        body { bindEntireRequestFromBody() }
    }
}
