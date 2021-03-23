package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

// ---

typealias FileMetadataAddMetadataRequest = BulkRequest<FileMetadataCreateRequestItem>

@Serializable
data class FileMetadataCreateRequestItem(
    override val path: String,
    val metadata: FileMetadataDocument,
) : WithPath
typealias FileMetadataAddMetadataResponse = Unit

typealias FileMetadataMoveRequest = BulkRequest<FileMetadataMoveRequestItem>

@Serializable
data class FileMetadataMoveRequestItem(
    override val oldPath: String,
    override val newPath: String,
) : WithPathMoving
typealias FileMetadataMoveResponse = Unit

typealias FileMetadataDeleteRequest = BulkRequest<FileMetadataDeleteRequestItem>

@Serializable
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
