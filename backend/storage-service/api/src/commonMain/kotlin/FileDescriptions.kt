package dk.sdu.cloud.file.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dk.sdu.cloud.file.api.AccessRight as FileAccessRight

@Serializable
data class UpdateAclRequest(
    val path: String,
    val changes: List<AclEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

@Deprecated(
    replaceWith = ReplaceWith("AclEntryRequest(entity, rights, revoke)"),
    message = "Replace with AclEntryRequest"
)
typealias ACLEntryRequest = AclEntryRequest

@Serializable
data class AclEntryRequest(
    val entity: ACLEntity.User,
    val rights: Set<FileAccessRight>,
    val revoke: Boolean = false
)

@Serializable
data class UpdateProjectAclRequest(
    val path: String,
    val project: String,
    val newAcl: List<ProjectAclEntryRequest>
)

@Serializable
data class ProjectAclEntryRequest(
    val group: String,
    val rights: Set<FileAccessRight>
)

typealias UpdateProjectAclResponse = Unit

@Serializable
data class StatRequest(
    val path: String,
    val attributes: String? = null
)

@Serializable
data class FindByPath(val path: String)

@Serializable
data class CreateDirectoryRequest(
    val path: String,
    val owner: String? = null,
    val sensitivity: SensitivityLevel? = null
)

@Serializable
data class ExtractRequest(
    val path: String,
    val removeOriginalArchive: Boolean? = null,
)

@Serializable
enum class FileSortBy {
    TYPE,
    PATH,
    CREATED_AT,
    MODIFIED_AT,
    SIZE,
    SENSITIVITY
}

@Serializable
enum class SortOrder {
    ASCENDING,
    DESCENDING
}

@Serializable
data class ReclassifyRequest(val path: String, val sensitivity: SensitivityLevel? = null)

/**
 * Audit entry for operations that work with a single file
 *
 * The original request is stored in [SingleFileAudit.request].
 */
@Serializable
data class SingleFileAudit<Request>(val request: Request)

/**
 * Audit entry for operations that work with bulk files
 *
 * The original request is stored in [BulkFileAudit.request].
 */
@Serializable
data class BulkFileAudit<Request>(val request: Request)

@Serializable
@Suppress("EnumEntryName")
enum class StorageFileAttribute {
    fileType,
    path,
    createdAt,
    modifiedAt,
    ownerName,
    size,
    acl,
    sensitivityLevel,
    ownSensitivityLevel,
    creator
}

@Serializable
data class ListDirectoryRequest(
    val path: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
    val order: SortOrder? = null,
    val sortBy: FileSortBy? = null,
    val attributes: String? = null,
    val type: FileType? = null
) : WithPaginationRequest

@Serializable
data class LookupFileInDirectoryRequest(
    val path: String,
    val itemsPerPage: Int,
    val order: SortOrder,
    val sortBy: FileSortBy,
    val attributes: String? = null
)

@Serializable
data class DeleteFileRequest(val path: String)

@Serializable
data class MoveRequest(val path: String, val newPath: String, val policy: WriteConflictPolicy? = null)

@Serializable
data class CopyRequest(val path: String, val newPath: String, val policy: WriteConflictPolicy? = null)

@Serializable
data class FindHomeFolderRequest(val username: String)
@Serializable
data class FindHomeFolderResponse(val path: String)

val DOWNLOAD_FILE_SCOPE = FileDescriptions.download.authDescription.requiredScope

@Serializable
data class DownloadByURI(
    val path: String,
    val token: String? = null,
) {
    override fun toString(): String = "DownloadByURI($path)"
}

@Serializable
@TSDefinition("""
export interface LongRunningResponse {
    type: string;
}
""")
sealed class LongRunningResponse {
    @Serializable
    @SerialName("timeout")
    @TSDefinition("""
export interface Timeout<T> extends LongRunningResponse {
    type: "timeout"
}
""")
    data class Timeout(
        val why: String = "The operation has timed out and will continue in the background"
    ) : LongRunningResponse()

    @Serializable
    @SerialName("result")
    @TSDefinition("""
export interface Ok extends LongRunningResponse {
    type: "result"
}
""")
    class Ok() : LongRunningResponse()
}

@Serializable
sealed class KnowledgeMode {
    /**
     * Ensures that the user can list the file. Concretely this means that we must be able to list the file in the
     * parent directory.
     */
    @Serializable
    @SerialName("list")
    class List : KnowledgeMode()

    /**
     * Ensures that the user has specific permissions on the file. If [requireWrite] is true read+write permissions
     * are required otherwise only read permissions are required. No permissions on the parent directory is required.
     */
    @Serializable
    @SerialName("permission")
    class Permission(val requireWrite: Boolean) : KnowledgeMode()
}

@Serializable
data class VerifyFileKnowledgeRequest(val user: String, val files: List<String>, val mode: KnowledgeMode? = null)
@Serializable
data class VerifyFileKnowledgeResponse(val responses: List<Boolean>)

@Serializable
data class NormalizePermissionsRequest(val path: String)
typealias NormalizePermissionsResponse = Unit

@Serializable
data class CreatePersonalRepositoryRequest(val project: String, val username: String) {
    init {
        // Some quick sanity checks (these should already be enforced elsewhere)
        if (project.contains("..")) throw RPCException("Project cannot contain '..'", HttpStatusCode.BadRequest)
        if (project.contains("/")) throw RPCException("Project cannot contains '/'", HttpStatusCode.BadRequest)
        if (username.contains("..")) throw RPCException("Username cannot contain '..'", HttpStatusCode.BadRequest)
        if (username.contains("/")) throw RPCException("Username cannot contains '/'", HttpStatusCode.BadRequest)
    }
}

typealias CreatePersonalRepositoryResponse = Unit

@Serializable
data class RetrieveQuotaRequest(
    val path: String,
    /**
     * Include own usage in response
     */
    val includeUsage: Boolean = false
)
typealias RetrieveQuotaResponse = Quota

@Serializable
data class Quota(
    /**
     * allocated quota from other projects
     */
    val quotaInTotal: Long,
    /**
     * quota which has not yet been allocated to sub-projects
     */
    val remainingQuota: Long,
    /**
     * quota which has been allocated to sub-projects
     */
    val allocatedToSubProjects: Long,
    /**
     * actual usage in the current project
     */
    val inProjectUsage: Long? = null
) {
    val quotaInBytes = remainingQuota
    val allocated = allocatedToSubProjects
    val quotaUsed = inProjectUsage
}

@Serializable
data class UpdateQuotaRequest(val path: String, val quotaInBytes: Long, val additive: Boolean = false)
typealias UpdateQuotaResponse = Unit

@Serializable
data class TransferQuotaRequest(val path: String, val quotaInBytes: Long)
typealias TransferQuotaResponse = Unit

const val NO_QUOTA = -1L

val Int.KiB: Long get() = 1024L * this
val Int.MiB: Long get() = 1024L * 1024 * this
val Int.GiB: Long get() = 1024L * 1024 * 1024 * this
val Int.TiB: Long get() = 1024L * 1024 * 1024 * 1024 * this
val Int.PiB: Long get() = 1024L * 1024 * 1024 * 1024 * 1024 * this

@TSTopLevel
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
                +boundTo(ListDirectoryRequest::attributes)
                +boundTo(ListDirectoryRequest::type)
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
                    +boundTo(LookupFileInDirectoryRequest::attributes)
                }
            }
        }

    val stat = call<
            StatRequest,
            StorageFile,
            CommonErrorMessage>("stat") {
        audit<SingleFileAudit<StatRequest>>()

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
                +boundTo(StatRequest::path)
                +boundTo(StatRequest::attributes)
            }
        }
    }

    val createDirectory =
        call<CreateDirectoryRequest, LongRunningResponse, CommonErrorMessage>("createDirectory") {
            auth {
                roles = Roles.AUTHENTICATED
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

    val deleteFile = call<DeleteFileRequest, LongRunningResponse, CommonErrorMessage>("deleteFile") {
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

    val download = call<DownloadByURI, Unit, CommonErrorMessage>("download") {
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

    val move = call<MoveRequest, LongRunningResponse, CommonErrorMessage>("move") {
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

    val copy = call<CopyRequest, LongRunningResponse, CommonErrorMessage>("copy") {
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

    val verifyFileKnowledge = call<
            VerifyFileKnowledgeRequest,
            VerifyFileKnowledgeResponse,
            CommonErrorMessage>("verifyFileKnowledge")
    {
        auth {
            roles = Roles.PRIVILEGED
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

    val updateProjectAcl =
        call<UpdateProjectAclRequest, UpdateProjectAclResponse, CommonErrorMessage>("updateProjectAcl") {
            auth {
                access = AccessRight.READ_WRITE
            }

            websocket(wsBaseContext)
            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"update-project-acl"
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
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
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

    val normalizePermissions =
        call<NormalizePermissionsRequest, NormalizePermissionsResponse, CommonErrorMessage>("normalizePermissions") {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.AUTHENTICATED
            }

            websocket(wsBaseContext)

            http {
                method = HttpMethod.Post
                path {
                    using(baseContext)
                    +"normalize-permissions"
                }

                body { bindEntireRequestFromBody() }
            }
        }

    /**
     * Internal call to ensure that a personal repository has been created for a user.
     */
    val createPersonalRepository =
        call<CreatePersonalRepositoryRequest, CreatePersonalRepositoryResponse, CommonErrorMessage>("createPersonalRepository") {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.PRIVILEGED
            }

            websocket(wsBaseContext)

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"create-personal-repository"
                }

                body { bindEntireRequestFromBody() }
            }
        }

    val retrieveQuota = call<RetrieveQuotaRequest, RetrieveQuotaResponse, CommonErrorMessage>("retrieveQuota") {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"quota"
            }

            params {
                +boundTo(RetrieveQuotaRequest::path)
                +boundTo(RetrieveQuotaRequest::includeUsage)
            }
        }
    }

    /**
     * Updates the quota of a subproject
     */
    val updateQuota = call<UpdateQuotaRequest, UpdateQuotaResponse, CommonErrorMessage>("updateQuota") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        websocket(wsBaseContext)

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"quota"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Transfers quota to a personal project
     */
    val transferQuota = call<TransferQuotaRequest, TransferQuotaResponse, CommonErrorMessage>("transferQuota") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"transfer-quota"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
