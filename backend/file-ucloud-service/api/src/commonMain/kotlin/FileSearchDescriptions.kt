package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.PartialUFile
import dk.sdu.cloud.file.orchestrator.api.UFile
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * @see FileSearchDescriptions.advancedSearch
 */
@Serializable
data class AdvancedSearchRequest(
    val fileName: String? = null,
    val extensions: List<String>? = null,
    val fileTypes: List<FileType>? = null,

    val includeShares: Boolean? = null,

    override val itemsPerPage: Int? = null,
    override val itemsToSkip: Long?,
    override val consistency: PaginationRequestV2Consistency?,
    override val next: String?
) : WithPaginationRequestV2

typealias SearchResult = PartialUFile

/**
 * Contains REST calls for searching in files
 */
@TSTopLevel
object FileSearchDescriptions : CallDescriptionContainer("fileSearch") {
    const val baseContext: String = "/api/file-search"

    val advancedSearch = call<AdvancedSearchRequest, PageV2<SearchResult>, CommonErrorMessage>("advancedSearch") {
        httpSearch(baseContext)
    }
}
