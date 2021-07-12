package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

// ---

typealias FileMetadataAddMetadataRequest = BulkRequest<FileMetadataAddRequestItem>

@Serializable
data class FileMetadataAddRequestItem(
    override val id: String,
    val metadata: FileMetadataDocument.Spec,
) : WithPath
typealias FileMetadataAddMetadataResponse = Unit

typealias FileMetadataMoveRequest = BulkRequest<FileMetadataMoveRequestItem>

@Serializable
data class FileMetadataMoveRequestItem(
    override val oldId: String,
    override val newId: String,
) : WithPathMoving
typealias FileMetadataMoveResponse = Unit

typealias FileMetadataDeleteRequest = BulkRequest<FileMetadataDeleteRequestItem>

@Serializable
data class FileMetadataDeleteRequestItem(
    override val id: String,
    val templateId: String,
) : WithPath
typealias FileMetadataDeleteResponse = Unit

@Serializable
data class FileMetadataRetrieveAllRequest(
    val parentPath: String,
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

    /*
    // TODO Interface TBD
    val search = call<FileMetadataSearchRequest, FileMetadataSearchResponse, CommonErrorMessage>("search") {
        httpSearch(baseContext)
    }
     */
}
