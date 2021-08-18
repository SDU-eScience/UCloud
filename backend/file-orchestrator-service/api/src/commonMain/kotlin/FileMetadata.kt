package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// ---

typealias FileMetadataAddMetadataRequest = BulkRequest<FileMetadataAddRequestItem>

@Serializable
data class FileMetadataAddRequestItem(
    val fileId: String,
    val metadata: FileMetadataDocument.Spec,
)
typealias FileMetadataAddMetadataResponse = Unit

typealias FileMetadataMoveRequest = BulkRequest<FileMetadataMoveRequestItem>

@Serializable
data class FileMetadataMoveRequestItem(
    val oldFileId: String,
    val newFileId: String,
)
typealias FileMetadataMoveResponse = Unit

typealias FileMetadataDeleteRequest = BulkRequest<FileMetadataDeleteRequestItem>
@Serializable
data class FileMetadataDeleteRequestItem(
    override val id: String,
    val changeLog: String,
) : WithStringId
typealias FileMetadataDeleteResponse = Unit

@Serializable
data class FileMetadataRetrieveAllRequest(
    val fileId: String,
)

@Serializable
data class FileMetadataRetrieveAllResponse(
    val metadata: List<FileMetadataAttached>
)

@Serializable
data class FileMetadataAttached(val path: String, val metadata: FileMetadataDocument)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class FileMetadataBrowseRequest(
    @UCloudApiDoc("Filters on the `templateId` attribute of metadata documents")
    val filterTemplate: String? = null,
    @UCloudApiDoc("Filters on the `version` attribute of metadata documents." +
            "Requires `filterTemplate` to be specified`")
    val filterVersion: String? = null,
    @UCloudApiDoc("Determines if this should only fetch document which have status `not_required` or `approved`")
    val filterActive: Boolean = true,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2 {
    init {
        if (filterVersion != null && filterTemplate == null) {
            throw RPCException("filterVersion cannot be specified without filterTemplate", HttpStatusCode.BadRequest)
        }
    }
}

typealias FileMetadataBrowseResponse = PageV2<FileMetadataDocument>

// ---

object FileMetadata : CallDescriptionContainer("files.metadata") {
    const val baseContext = "/api/files/metadata"

    val create = call<FileMetadataAddMetadataRequest, FileMetadataAddMetadataResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val move = call<FileMetadataMoveRequest, FileMetadataMoveResponse,
        CommonErrorMessage>("moveMetadata") {
        httpUpdate(baseContext, "move")
    }

    val delete = call<FileMetadataDeleteRequest, FileMetadataDeleteResponse,
        CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val retrieveAll = call<FileMetadataRetrieveAllRequest, FileMetadataRetrieveAllResponse,
        CommonErrorMessage>("retrieveAll") {
        httpRetrieve(baseContext, "all")
    }

    val approve = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("approve") {
        httpUpdate(baseContext, "approve")
    }

    val reject = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("reject") {
        httpUpdate(baseContext, "reject")
    }

    val browse = call<FileMetadataBrowseRequest, FileMetadataBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)

        documentation {
            summary = "Browses metadata documents"
            description = """
                Browses in all accessible metadata documents of a user. These are potentially filtered via the flags
                provided in the request, such as filtering for specific templates. This endpoint should consider any
                `FileCollection` that the user has access to in the currently active project. Note that this endpoint
                can only return information about the metadata documents and not the file contents itself. Clients
                should generally present the output of this has purely metadata documents, they can link to the real
                files if needed. This should eventually result in either a `browse` or `retrieve` call in the files API.
            """.trimIndent()
        }
    }


    /*
    // TODO Interface TBD
    val search = call<FileMetadataSearchRequest, FileMetadataSearchResponse, CommonErrorMessage>("search") {
        httpSearch(baseContext)
    }
     */
}
