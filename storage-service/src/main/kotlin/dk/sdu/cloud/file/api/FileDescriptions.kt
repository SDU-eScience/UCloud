package dk.sdu.cloud.file.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.calls.websocket
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import dk.sdu.cloud.file.api.AccessRight as FileAccessRight

data class CreateLinkRequest(
    val linkPath: String,
    val linkTargetPath: String
)

data class UpdateAclRequest(
    val path: String,
    val recurse: Boolean,
    val changes: List<ACLEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

data class ACLEntryRequest(
    val entity: String,
    val rights: Set<FileAccessRight>,
    val revoke: Boolean = false,
    val isUser: Boolean = true
)

data class ChmodRequest(
    val path: String,
    val owner: Set<FileAccessRight>,
    val group: Set<FileAccessRight>,
    val other: Set<FileAccessRight>,
    val recurse: Boolean
)

data class FindByPath(val path: String)

data class CreateDirectoryRequest(
    val path: String,
    val owner: String?,
    val sensitivity: SensitivityLevel? = null
)

data class ExtractRequest(
    val path: String
)

enum class FileSortBy {
    TYPE,
    PATH,
    CREATED_AT,
    MODIFIED_AT,
    SIZE,
    ACL,
    SENSITIVITY,
    ANNOTATION
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class ReclassifyRequest(val path: String, val sensitivity: SensitivityLevel?)

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

data class FindHomeFolderRequest(val username: String)
data class FindHomeFolderResponse(val path: String)

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
    val rootsToMaterialized: Map<String, List<StorageFile>>
)

data class DeliverMaterializedFileSystemResponse(
    val shouldContinue: Map<String, Boolean>
)

object FileDescriptions : CallDescriptionContainer("files") {
    val baseContext = "/api/files"
    val wsBaseContext = "$baseContext/ws"

    val listAtPath = call<ListDirectoryRequest, Page<StorageFile>, CommonErrorMessage>("listAtPath") {
        audit<SingleFileAudit<ListDirectoryRequest>>()

        auth {
            access = AccessRight.READ
        }

        websocket(wsBaseContext)

        http {
            path { using(baseContext) }

            params {
                +boundTo(ListDirectoryRequest::path)
                +boundTo(ListDirectoryRequest::itemsPerPage)
                +boundTo(ListDirectoryRequest::page)
                +boundTo(ListDirectoryRequest::order)
                +boundTo(ListDirectoryRequest::sortBy)
            }

            headers {
                +"X-No-Load"
            }
        }
    }

    val lookupFileInDirectory =
        call<LookupFileInDirectoryRequest, Page<StorageFile>, CommonErrorMessage>("lookupFileInDirectory") {
            audit<SingleFileAudit<LookupFileInDirectoryRequest>>()

            auth {
                access = AccessRight.READ
            }

            websocket(wsBaseContext)

            http {
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

                headers {
                    +"X-No-Load"
                }
            }
        }

    val stat = call<
            FindByPath,
            StorageFile,
            CommonErrorMessage>("stat") {
        audit<SingleFileAudit<FindByPath>>()

        auth {
            access = AccessRight.READ
        }

        websocket(wsBaseContext)

        http {
            path {
                using(baseContext)
                +"stat"
            }

            params {
                +boundTo(FindByPath::path)
            }

            headers {
                +"X-No-Load"
            }
        }
    }

    val createDirectory =
        call<CreateDirectoryRequest, LongRunningResponse<Unit>, CommonErrorMessage>("createDirectory") {
            auth {
                access = AccessRight.READ_WRITE
            }

            websocket(wsBaseContext)

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"directory"
                }

                body {
                    bindEntireRequestFromBody()
                }
            }
        }

    val deleteFile = call<DeleteFileRequest, LongRunningResponse<Unit>, CommonErrorMessage>("deleteFile") {
        audit<SingleFileAudit<DeleteFileRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    val download = call<DownloadByURI, BinaryStream, CommonErrorMessage>("download") {
        audit<BulkFileAudit<FindByPath>>()
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"download"
            }

            params {
                +boundTo(DownloadByURI::path)
                +boundTo(DownloadByURI::token)
            }
        }
    }

    val move = call<MoveRequest, LongRunningResponse<Unit>, CommonErrorMessage>("move") {
        audit<SingleFileAudit<MoveRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)

        http {
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
    }

    val copy = call<CopyRequest, LongRunningResponse<Unit>, CommonErrorMessage>("copy") {
        audit<SingleFileAudit<CopyRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)

        http {
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
    }

    val bulkDownload = call<BulkDownloadRequest, BinaryStream, CommonErrorMessage>("bulkDownload") {
        audit<BulkFileAudit<BulkDownloadRequest>>()

        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Annotates a file with metadata. Privileged API.
     */
    val annotate = call<AnnotateFileRequest, Unit, CommonErrorMessage>("annotate") {
        audit<SingleFileAudit<AnnotateFileRequest>>()

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"annotate"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val verifyFileKnowledge = call<
            VerifyFileKnowledgeRequest,
            VerifyFileKnowledgeResponse,
            CommonErrorMessage>("verifyFileKnowledge")
    {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"verify-knowledge"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val deliverMaterializedFileSystem = call<
            DeliverMaterializedFileSystemRequest,
            DeliverMaterializedFileSystemResponse,
            CommonErrorMessage>("deliverMaterializedFileSystem")
    {
        audit<DeliverMaterializedFileSystemAudit>()

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"deliver-materialized"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val chmod = call<ChmodRequest, Unit, CommonErrorMessage>("chmod") {
        audit<BulkFileAudit<ChmodRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)
        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"chmod"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val updateAcl = call<UpdateAclRequest, Unit, CommonErrorMessage>("updateAcl") {
        audit<BulkFileAudit<UpdateAclRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)
        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update-acl"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val createLink = call<
            CreateLinkRequest,
            StorageFile,
            CommonErrorMessage>("createLink") {
        audit<SingleFileAudit<CreateLinkRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"create-link"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val reclassify = call<
            ReclassifyRequest,
            Unit,
            CommonErrorMessage>("reclassify") {
        audit<SingleFileAudit<ReclassifyRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(wsBaseContext)
        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"reclassify"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val extract = call<
            ExtractRequest,
            Unit,
            CommonErrorMessage>("extract") {
        audit<SingleFileAudit<ExtractRequest>>()

        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"extract"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findHomeFolder = call<FindHomeFolderRequest, FindHomeFolderResponse, CommonErrorMessage>("findHomeFolder") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEDGED
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"homeFolder"
            }

            params {
                +boundTo(FindHomeFolderRequest::username)
            }
        }
    }
}
