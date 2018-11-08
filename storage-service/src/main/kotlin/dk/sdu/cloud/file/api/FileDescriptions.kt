package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class FindByPath(val path: String)

data class CreateDirectoryRequest(
    val path: String,
    val owner: String?
)

enum class FileSortBy {
    TYPE,
    PATH,
    CREATED_AT,
    MODIFIED_AT,
    SIZE,
    ACL,
    FAVORITED,
    SENSITIVITY,
    ANNOTATION
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

/**
 * Audit entry for operations that work with a single file
 *
 * The original request is stored in [SingleFileAudit.request].
 *
 * The ID of the file is stored in [SingleFileAudit.fileId], this ID will correspond to the file targeted by this
 * operation. This file is typically found via some kind of query (for example, by path).
 */
data class SingleFileAudit<Request>(
    val fileId: String?,
    val request: Request
)

/**
 * Audit entry for operations that work with bulk files
 *
 * The original request is stored in [BulkFileAudit.request].
 *
 * The IDs of the files are stored in [BulkFileAudit.fileIds]. These IDs will correspond to the files targeted by the
 * operation. There will be an entry per query. It is assumed that the query is ordered, the IDs will be returned
 * in the same order. Files that cannot be resolved have an ID of null.
 */
data class BulkFileAudit<Request>(
    val fileIds: List<String?>,
    val request: Request
)

data class ListDirectoryRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    val order: SortOrder?,
    val sortBy: FileSortBy?
) : WithPaginationRequest

data class LookupFileInDirectoryRequest(
    val path: String,
    val itemsPerPage: Int,
    val order: SortOrder,
    val sortBy: FileSortBy
)

data class DeleteFileRequest(val path: String)

data class MoveRequest(val path: String, val newPath: String, val policy: WriteConflictPolicy? = null)

data class CopyRequest(val path: String, val newPath: String, val policy: WriteConflictPolicy? = null)

data class BulkDownloadRequest(val prefix: String, val files: List<String>)

data class SyncFileListRequest(val path: String, val modifiedSince: Long? = null)

data class AnnotateFileRequest(val path: String, val annotatedWith: String, val proxyUser: String) {
    init {
        validateAnnotation(annotatedWith)
        if (proxyUser.isBlank()) throw IllegalArgumentException("proxyUser cannot be blank")
    }
}

fun validateAnnotation(annotation: String) {
    if (annotation.contains(Regex("[0-9]"))) {
        throw IllegalArgumentException("Annotation reserved for future use")
    }

    if (annotation.contains(',') || annotation.contains('\n')) {
        throw IllegalArgumentException("Illegal annotation")
    }

    if (annotation.isEmpty()) throw IllegalArgumentException("Annotation cannot be empty")
    if (annotation.length > 1) {
        throw IllegalArgumentException("Annotation type reserved for future use")
    }
}

val DOWNLOAD_FILE_SCOPE = FileDescriptions.download.requiredAuthScope

data class DownloadByURI(val path: String, val token: String?)

data class FavoriteCommand(val path: String)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = LongRunningResponse.Timeout::class, name = "timeout"),
    JsonSubTypes.Type(value = LongRunningResponse.Result::class, name = "result")
)
sealed class LongRunningResponse<T> {
    data class Timeout<T>(
        val why: String = "The operation has timed out and will continue in the background"
    ) : LongRunningResponse<T>()

    data class Result<T>(
        val item: T
    ) : LongRunningResponse<T>()
}

data class VerifyFileKnowledgeRequest(val user: String, val files: List<String>)
data class VerifyFileKnowledgeResponse(val responses: List<Boolean>)

data class DeliverMaterializedFileSystemAudit(val roots: List<String>)
data class DeliverMaterializedFileSystemRequest(
    val rootsToMaterialized: Map<String, List<EventMaterializedStorageFile>>
)

data class DeliverMaterializedFileSystemResponse(
    val shouldContinue: Map<String, Boolean>
)

object FileDescriptions : RESTDescriptions("files") {
    val baseContext = "/api/files"

    val listAtPath = callDescriptionWithAudit<ListDirectoryRequest, Page<StorageFile>, CommonErrorMessage,
            SingleFileAudit<ListDirectoryRequest>> {
        name = "listAtPath"

        auth {
            access = AccessRight.READ
        }

        path { using(baseContext) }

        params {
            +boundTo(ListDirectoryRequest::path)
            +boundTo(ListDirectoryRequest::itemsPerPage)
            +boundTo(ListDirectoryRequest::page)
            +boundTo(ListDirectoryRequest::order)
            +boundTo(ListDirectoryRequest::sortBy)
        }
    }

    val lookupFileInDirectory = callDescriptionWithAudit<LookupFileInDirectoryRequest, Page<StorageFile>,
            CommonErrorMessage, SingleFileAudit<LookupFileInDirectoryRequest>> {
        name = "lookupFileInDirectory"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"lookup"
        }

        params {
            +boundTo(LookupFileInDirectoryRequest::path)
            +boundTo(LookupFileInDirectoryRequest::itemsPerPage)
            +boundTo(LookupFileInDirectoryRequest::sortBy)
            +boundTo(LookupFileInDirectoryRequest::order)
        }
    }

    val stat = callDescriptionWithAudit<FindByPath, StorageFile, CommonErrorMessage, SingleFileAudit<FindByPath>> {
        name = "stat"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"stat"
        }

        params {
            +boundTo(FindByPath::path)
        }
    }

    val markAsFavorite = callDescriptionWithAudit<FavoriteCommand, LongRunningResponse<Unit>, CommonErrorMessage,
            SingleFileAudit<FavoriteCommand>> {
        name = "markAsFavorite"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand::path)
        }
    }

    val removeFavorite = callDescriptionWithAudit<FavoriteCommand, LongRunningResponse<Unit>, CommonErrorMessage,
            SingleFileAudit<FavoriteCommand>> {
        name = "removeFavorite"
        method = HttpMethod.Delete

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand::path)
        }
    }

    val createDirectory = callDescription<CreateDirectoryRequest, LongRunningResponse<Unit>, CommonErrorMessage> {
        name = "createDirectory"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"directory"
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val deleteFile = callDescriptionWithAudit<DeleteFileRequest, LongRunningResponse<Unit>, CommonErrorMessage,
            SingleFileAudit<DeleteFileRequest>> {
        name = "deleteFile"
        method = HttpMethod.Delete

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val download = callDescriptionWithAudit<DownloadByURI, Unit, CommonErrorMessage, BulkFileAudit<FindByPath>> {
        name = "download"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"download"
        }

        params {
            +boundTo(DownloadByURI::path)
            +boundTo(DownloadByURI::token)
        }
    }

    val move = callDescriptionWithAudit<MoveRequest, LongRunningResponse<Unit>, CommonErrorMessage,
            SingleFileAudit<MoveRequest>> {
        name = "move"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"move"
        }

        params {
            +boundTo(MoveRequest::path)
            +boundTo(MoveRequest::newPath)
            +boundTo(MoveRequest::policy)
        }
    }

    val copy = callDescriptionWithAudit<CopyRequest, LongRunningResponse<Unit>, CommonErrorMessage,
            SingleFileAudit<CopyRequest>> {
        name = "copy"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"copy"
        }

        params {
            +boundTo(CopyRequest::path)
            +boundTo(CopyRequest::newPath)
            +boundTo(CopyRequest::policy)
        }
    }

    val bulkDownload = callDescriptionWithAudit<BulkDownloadRequest, Unit, CommonErrorMessage,
            BulkFileAudit<BulkDownloadRequest>> {
        name = "bulkDownload"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"bulk"
        }

        body { bindEntireRequestFromBody() }
    }

    val syncFileList = callDescriptionWithAudit<SyncFileListRequest, Unit, CommonErrorMessage,
            SingleFileAudit<SyncFileListRequest>> {
        name = "syncFileList"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"sync"
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Annotates a file with metadata. Privileged API.
     */
    val annotate = callDescriptionWithAudit<AnnotateFileRequest, Unit, CommonErrorMessage,
            SingleFileAudit<AnnotateFileRequest>> {
        name = "annotate"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"annotate"
        }

        body { bindEntireRequestFromBody() }
    }

    val verifyFileKnowledge = callDescription<
            VerifyFileKnowledgeRequest,
            VerifyFileKnowledgeResponse,
            CommonErrorMessage>
    {
        name = "verifyFileKnowledge"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"verify-knowledge"
        }

        body { bindEntireRequestFromBody() }
    }

    val deliverMaterializedFileSystem = callDescriptionWithAudit<
            DeliverMaterializedFileSystemRequest,
            DeliverMaterializedFileSystemResponse,
            CommonErrorMessage,
            DeliverMaterializedFileSystemAudit>
    {
        name = "deliverMaterializedFileSystem"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"deliver-materialized"
        }

        body { bindEntireRequestFromBody() }
    }
}
