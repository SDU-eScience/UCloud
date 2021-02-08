package dk.sdu.cloud.file.orchestrator

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.WithPaginationRequestV2

interface FilesIncludeFlags {
    val includePermissions: Boolean?
    val includeTimestamps: Boolean?
    val includeSizes: Boolean?
    val includeUnixInfo: Boolean?
    val includeMetadata: Boolean?

    @UCloudApiDoc("""Determines if the request should succeed if the underlying system does not support this data.

This value is `true` by default """)
    val allowUnsupportedInclude: Boolean?
}

interface WithConflictPolicy {
    val conflictPolicy: WriteConflictPolicy
}

interface WithPath {
    val path: String
}

interface WithPathMoving {
    val oldPath: String
    val newPath: String
}

data class FindByPath(override val path: String) : WithPath

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = LongRunningTask.Complete::class, name = "complete"),
    JsonSubTypes.Type(value = LongRunningTask.ContinuesInBackground::class, name = "continues_in_background")
)
sealed class LongRunningTask<V> {
    class Complete<V>(val result: V) : LongRunningTask<V>()
    class ContinuesInBackground<V>(val taskId: String) : LongRunningTask<V>()
}

// ---

data class FilesBrowseRequest(
    override val path: String,
    override val includePermissions: Boolean? = null,
    override val includeTimestamps: Boolean? = null,
    override val includeSizes: Boolean? = null,
    override val includeUnixInfo: Boolean? = null,
    override val includeMetadata: Boolean? = null,
    override val allowUnsupportedInclude: Boolean? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2, FilesIncludeFlags, WithPath
typealias FilesBrowseResponse = PageV2<UFile>

data class FilesRetrieveRequest(
    override val path: String,
    override val includePermissions: Boolean? = null,
    override val includeTimestamps: Boolean? = null,
    override val includeSizes: Boolean? = null,
    override val includeUnixInfo: Boolean? = null,
    override val includeMetadata: Boolean? = null,
    override val allowUnsupportedInclude: Boolean? = null,
) : WithPath, FilesIncludeFlags
typealias FilesRetrieveResponse = UFile

typealias FilesMoveRequest = BulkRequest<FilesMoveRequestItem>
data class FilesMoveRequestItem(
    override val oldPath: String,
    override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesMoveResponse = BulkResponse<LongRunningTask<FindByPath>>

typealias FilesCopyRequest = BulkRequest<FilesCopyRequestItem>
data class FilesCopyRequestItem(
    override val oldPath: String,
    override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesCopyResponse = BulkResponse<LongRunningTask<FindByPath>>

typealias FilesDeleteRequest = BulkRequest<FindByPath>
typealias FilesDeleteResponse = BulkResponse<LongRunningTask<Unit>>

typealias FilesDownloadRequest = FindByPath
typealias FilesDownloadResponse = BinaryStream

typealias FilesCreateFolderRequest = BulkRequest<FilesCreateFolderRequestItem>
data class FilesCreateFolderRequestItem(
    override val path: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesCreateFolderResponse = BulkResponse<FindByPath>

typealias FilesUpdateAclRequest = BulkRequest<FilesUpdateAclRequestItem>
data class FilesUpdateAclRequestItem(
    override val path: String,
    val newAcl: List<ResourceAclEntry<FilePermission>>
) : WithPath
typealias FilesUpdateAclResponse = Unit

data class FilesUploadRequest(
    override val path: String,
    override val conflictPolicy: WriteConflictPolicy,
    val contents: BinaryStream,
) : WithConflictPolicy, WithPath
typealias FilesUploadResponse = FindByPath
data class FilesUploadAudit(
    override val path: String,
    override val conflictPolicy: WriteConflictPolicy,
    val resultingPath: String,
) : WithPath, WithConflictPolicy

typealias FilesTrashRequest = BulkRequest<FindByPath>
typealias FilesTrashResponse = LongRunningTask<Unit>

typealias FilesCreateUploadRequest = BulkRequest<FilesCreateUploadRequestItem>
data class FilesCreateUploadRequestItem(
    override val path: String
) : WithPath
typealias FilesCreateUploadResponse = BulkResponse<FilesCreateUploadResponseItem>
data class FilesCreateUploadResponseItem(val endpoint: String)

typealias FilesCreateDownloadRequest = BulkRequest<FilesCreateDownloadRequestItem>
data class FilesCreateDownloadRequestItem(override val path: String) : WithPath
typealias FilesCreateDownloadResponse = BulkResponse<FilesCreateDownloadResponseItem>
data class FilesCreateDownloadResponseItem(val endpoint: String)

// ---

object Files : CallDescriptionContainer("files") {
    const val baseContext = "/api/files"

    val browse = call<FilesBrowseRequest, FilesBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val retrieve = call<FilesRetrieveRequest, FilesRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val move = call<FilesMoveRequest, FilesMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move")
    }

    val copy = call<FilesCopyRequest, FilesCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy")
    }

    val delete = call<FilesDeleteRequest, FilesDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val createUpload = call<FilesCreateUploadRequest, FilesCreateUploadResponse, CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload")
    }

    val createDownload = call<FilesCreateDownloadRequest, FilesCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download")
    }

    val createFolder = call<FilesCreateFolderRequest, FilesCreateFolderResponse, CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder")
    }

    val updateAcl = call<FilesUpdateAclRequest, FilesUpdateAclResponse, CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    val trash = call<FilesTrashRequest, FilesTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash")
    }

    /*
    // TODO Interface tbd
    val search = call<FilesSearchRequest, FilesSearchResponse, CommonErrorMessage>("search") {
    }
     */
}
