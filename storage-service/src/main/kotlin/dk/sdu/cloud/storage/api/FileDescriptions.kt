package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.Page
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

data class ListDirectoryRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?,
    val order: SortOrder?,
    val sortBy: FileSortBy?
) : WithPagination

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

data class MarkFileAsOpenAccessRequest(val path: String, val proxyUser: String) {
    init {
        if (path.isBlank()) throw IllegalArgumentException("path cannot be empty")
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

object FileDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/files"

    val listAtPath = callDescription<ListDirectoryRequest, Page<StorageFile>, CommonErrorMessage> {
        prettyName = "filesListAtPath"
        path { using(baseContext) }
        params {
            +boundTo(ListDirectoryRequest::path)
            +boundTo(ListDirectoryRequest::itemsPerPage)
            +boundTo(ListDirectoryRequest::page)
            +boundTo(ListDirectoryRequest::order)
            +boundTo(ListDirectoryRequest::sortBy)
        }
    }

    val lookupFileInDirectory = callDescription<LookupFileInDirectoryRequest, Page<StorageFile>, CommonErrorMessage> {
        prettyName = "lookupFileInDirectory"

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

    val stat = callDescription<FindByPath, StorageFile, CommonErrorMessage> {
        prettyName = "stat"
        path {
            using(baseContext)
            +"stat"
        }

        params {
            +boundTo(FindByPath::path)
        }
    }

    val markAsFavorite = callDescription<FavoriteCommand.Grant, Unit, CommonErrorMessage> {
        prettyName = "filesMarkAsFavorite"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand.Grant::path)
        }
    }

    val removeFavorite = callDescription<FavoriteCommand.Revoke, Unit, CommonErrorMessage> {
        prettyName = "filesRemoveAsFavorite"
        method = HttpMethod.Delete

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand.Revoke::path)
        }
    }

    val createDirectory = callDescription<CreateDirectoryRequest, Unit, CommonErrorMessage> {
        prettyName = "createDirectory"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"directory"
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val deleteFile = callDescription<DeleteFileRequest, Unit, CommonErrorMessage> {
        prettyName = "deleteFile"
        method = HttpMethod.Delete

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val download = callDescription<DownloadByURI, Unit, CommonErrorMessage> {
        prettyName = "filesDownload"
        path {
            using(baseContext)
            +"download"
        }

        params {
            +boundTo(DownloadByURI::path)
            +boundTo(DownloadByURI::token)
        }
    }

    val move = callDescription<MoveRequest, Unit, CommonErrorMessage> {
        prettyName = "move"
        method = HttpMethod.Post
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

    val copy = callDescription<CopyRequest, Unit, CommonErrorMessage> {
        prettyName = "copy"
        method = HttpMethod.Post
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

    val bulkDownload = callDescription<BulkDownloadRequest, Unit, CommonErrorMessage> {
        prettyName = "filesBulkDownload"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"bulk"
        }

        body { bindEntireRequestFromBody() }
    }

    val syncFileList = callDescription<SyncFileListRequest, Unit, CommonErrorMessage> {
        prettyName = "filesSyncFileList"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"sync"
        }

        body { bindEntireRequestFromBody() }
    }

    /**
     * Annotates a file with metadata. Privileged API.
     */
    val annotate = callDescription<AnnotateFileRequest, Unit, CommonErrorMessage> {
        prettyName = "filesAnnotate"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"annotate"
        }

        body { bindEntireRequestFromBody() }
    }
}

const val DOWNLOAD_FILE_SCOPE = "downloadFile"

data class DownloadByURI(val path: String, val token: String)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FavoriteCommand.Grant::class, name = "grant"),
    JsonSubTypes.Type(value = FavoriteCommand.Revoke::class, name = "revoke")
)
sealed class FavoriteCommand {
    abstract val path: String

    data class Grant(override val path: String) : FavoriteCommand()
    data class Revoke(override val path: String) : FavoriteCommand()
}