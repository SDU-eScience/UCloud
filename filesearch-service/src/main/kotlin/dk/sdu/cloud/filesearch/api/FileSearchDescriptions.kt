package dk.sdu.cloud.filesearch.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

/**
 * @see FileSearchDescriptions.simpleSearch
 */
data class SimpleSearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

/**
 * @see FileSearchDescriptions.advancedSearch
 */
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

typealias SearchResult = StorageFile

/**
 * Contains REST calls for searching in files
 */
object FileSearchDescriptions : CallDescriptionContainer("fileSearch") {
    const val baseContext: String = "/api/file-search"

    val simpleSearch = call<SimpleSearchRequest, Page<SearchResult>, CommonErrorMessage>("simpleSearch") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(SimpleSearchRequest::query)
                +boundTo(SimpleSearchRequest::itemsPerPage)
                +boundTo(SimpleSearchRequest::page)
            }

            headers {
                +"X-No-Load"
            }
        }
    }

    val advancedSearch = call<AdvancedSearchRequest, Page<SearchResult>, CommonErrorMessage>("advancedSearch") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"advanced"
            }

            body { bindEntireRequestFromBody() }

            headers {
                +"X-No-Load"
            }
        }
    }
}
