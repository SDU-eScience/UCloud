package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.calls.*
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

    /*
    // TODO Interface TBD
    val search = call<FileMetadataSearchRequest, FileMetadataSearchResponse, CommonErrorMessage>("search") {
        httpSearch(baseContext)
    }
     */
}
