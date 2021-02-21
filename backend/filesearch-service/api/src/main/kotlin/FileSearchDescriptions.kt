package dk.sdu.cloud.filesearch.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

/**
 * @see FileSearchDescriptions.advancedSearch
 */
data class AdvancedSearchRequest(
    val fileName: String?,
    val extensions: List<String>?,
    val fileTypes: List<FileType>?,

    val includeShares: Boolean?,

    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

typealias SearchResult = StorageFile

/**
 * Contains REST calls for searching in files
 */
@TSTopLevel
object FileSearchDescriptions : CallDescriptionContainer("fileSearch") {
    const val baseContext: String = "/api/file-search"

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
        }
    }
}
