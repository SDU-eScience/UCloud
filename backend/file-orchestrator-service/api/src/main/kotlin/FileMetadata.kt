package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*

// ---

typealias FileMetadataAddMetadataRequest = BulkRequest<FileMetadataCreateRequestItem>

data class FileMetadataCreateRequestItem(
    override val path: String,
    val metadata: FileMetadataDocument,
) : WithPath
typealias FileMetadataAddMetadataResponse = Unit

typealias FileMetadataMoveRequest = BulkRequest<FileMetadataMoveRequestItem>

data class FileMetadataMoveRequestItem(
    override val oldPath: String,
    override val newPath: String,
) : WithPathMoving
typealias FileMetadataMoveResponse = Unit

typealias FileMetadataDeleteRequest = BulkRequest<FileMetadataDeleteRequestItem>

data class FileMetadataDeleteRequestItem(
    override val path: String,
    val templateId: String,
) : WithPath
typealias FileMetadataDeleteResponse = Unit

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

    /*
    // TODO Interface TBD
    val search = call<FileMetadataSearchRequest, FileMetadataSearchResponse, CommonErrorMessage>("search") {
        httpSearch(baseContext)
    }
     */
}
