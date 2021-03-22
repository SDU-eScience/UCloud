package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class FindByPath(override val path: String) : WithPath

@Serializable
sealed class LongRunningTask<V> {
    @Serializable
    @SerialName("complete")
    class Complete<V>(val result: V) : LongRunningTask<V>()
    @Serializable
    @SerialName("continues_in_background")
    class ContinuesInBackground<V>(val taskId: String) : LongRunningTask<V>()
}

// ---

@Serializable
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

@Serializable
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
@Serializable
data class FilesMoveRequestItem(
    override val oldPath: String,
    override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesMoveResponse = BulkResponse<LongRunningTask<FindByPath>>

typealias FilesCopyRequest = BulkRequest<FilesCopyRequestItem>
@Serializable
data class FilesCopyRequestItem(
    override val oldPath: String, override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesCopyResponse = BulkResponse<LongRunningTask<FindByPath>>

typealias FilesDeleteRequest = BulkRequest<FindByPath>
typealias FilesDeleteResponse = BulkResponse<LongRunningTask<Unit>>

typealias FilesCreateFolderRequest = BulkRequest<FilesCreateFolderRequestItem>
@Serializable
data class FilesCreateFolderRequestItem(
    override val path: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesCreateFolderResponse = BulkResponse<FindByPath>

typealias FilesUpdateAclRequest = BulkRequest<FilesUpdateAclRequestItem>
@Serializable
data class FilesUpdateAclRequestItem(
    override val path: String,
    val newAcl: List<ResourceAclEntry<FilePermission>>
) : WithPath
typealias FilesUpdateAclResponse = Unit

typealias FilesTrashRequest = BulkRequest<FindByPath>
typealias FilesTrashResponse = BulkResponse<LongRunningTask<Unit>>

typealias FilesCreateUploadRequest = BulkRequest<FilesCreateUploadRequestItem>
@Serializable
data class FilesCreateUploadRequestItem(
    override val path: String
) : WithPath
typealias FilesCreateUploadResponse = BulkResponse<FilesCreateUploadResponseItem>
@Serializable
data class FilesCreateUploadResponseItem(val endpoint: String)

typealias FilesCreateDownloadRequest = BulkRequest<FilesCreateDownloadRequestItem>
@Serializable
data class FilesCreateDownloadRequestItem(override val path: String) : WithPath
typealias FilesCreateDownloadResponse = BulkResponse<FilesCreateDownloadResponseItem>
@Serializable
data class FilesCreateDownloadResponseItem(val endpoint: String)

// ---

object Files : CallDescriptionContainer("files") {
    const val baseContext = "/api/files"

    init {
        title = "Files"

        //language=markdown
        description = """Files in UCloud is a resource for storing, retrieving and organizing data in UCloud.

A file in UCloud (`UFile`) closely follows the concept of a computer file you might already be familiar with. The
functionality of a file is mostly determined by its `type`. The two most important types are the `DIRECTORY` and `FILE`
types. A `DIRECTORY` is a container of `UFile`s. A directory can itself contain more directories, which leads to a
natural tree-like structure. `FILE`s, also referred to as a regular files, are data records which each contain a series
of bytes.
"""
    }

    val browse = call<FilesBrowseRequest, FilesBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)

        documentation {
            summary = "Browse files of a directory"
            description = """
                Browses the contents of a directory.
                
                The results will be returned using the standard pagination API of UCloud. Consistency is slightly
                relaxed for this endpoint as it is typically hard to enforce for filesystems. Provider's are heavily
                encouraged to try and find all files on the first request and return information about them in
                subsequent requests. For example, a client might list all file names in the initial request and use
                this list for all subsequent requests and retrieve additional information about the files. If the files
                no longer exist then the provider should simply not include these results.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.BadRequest
                description = "File at the requested path is not a directory"
            }

            error {
                statusCode = HttpStatusCode.NotFound
                description = "The requested file does not exist or you do not have sufficient permissions"
            }
        }
    }

    val retrieve = call<FilesRetrieveRequest, FilesRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)

        documentation {
            summary = "Retrieves information about a single file"
            description = """
                Retrieves information about a single file.
                
                This file can be of any type. Clients can request additional information about the file using the
                `include*` flags of the request. Note that not all providers support all information. Clients can query
                this information using ${docCallRef(FileCollections::browse)} or 
                ${docCallRef(FileCollections::retrieve)} with the `includeSupport` flag.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "The requested file does not exist or you do not have sufficient permissions"
            }
        }
    }

    val move = call<FilesMoveRequest, FilesMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move")

        documentation {
            summary = "Move a file from one path to another"
            description = """
                Moves a file from one path to another.
                
                The file can be of any type. This request is also used for 'renames' of a file. This is simply
                considered a move within a single directory. This operation handles conflicts depending on the supplied
                `WriteConflictPolicy`.
                
                This is a long running task. As a result, this operation might respond with a status code which indicate
                that it will continue in the background. Progress of this job can be followed using the task API.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.BadRequest
                description = "The operation couldn't be completed because of the write conflict policy"
            }

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val copy = call<FilesCopyRequest, FilesCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy")

        documentation {
            summary = "Copies a file from one path to another"
            description = """
                Copies a file from one path to another.
                
                The file can be of any type. If a directory is chosen then this will recursively copy all of its
                children. This request might fail half-way through. This can potentially lead to a situation where
                a partial file is left on the file-system. It is left to the user to clean up this file.
                
                This operation handles conflicts depending on the supplied `WriteConflictPolicy`.
                
                This is a long running task. As a result, this operation might respond with a status code which indicate
                that it will continue in the background. Progress of this job can be followed using the task API.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.BadRequest
                description = "The operation couldn't be completed because of the write conflict policy"
            }

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val delete = call<FilesDeleteRequest, FilesDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)

        documentation {
            summary = "Deletes a file permanently from the file-system"
            description = """
                Deletes a file permanently from the file-system.
                
                This operation is permanent and cannot be undone. User interfaces should prefer using
                ${docCallRef(::trash)} if it is supported by the provider.
                
                If the referenced file is a directory then this will delete all files recursively. This operation may
                fail half-way through which will leave the file-system in an inconsistent state. It is the user's
                responsibility to clean up this state.
                
                This is a long running task. As a result, this operation might respond with a status code which indicate
                that it will continue in the background. Progress of this job can be followed using the task API.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val createUpload = call<FilesCreateUploadRequest, FilesCreateUploadResponse, CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload")

        documentation {
            summary = "Creates an upload session between the user and the provider"
            description = """
                Creates an upload session between the user and the provider.
                
                The returned endpoint will accept an upload from the user which will create a file at a location
                specified in this request.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val createDownload = call<FilesCreateDownloadRequest, FilesCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download")

        documentation {
            summary = "Creates a download session between the user and the provider"
            description = """
                Creates a download session between the user and the provider.
                
                The returned endpoint will respond with a download to the user.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val createFolder = call<FilesCreateFolderRequest, FilesCreateFolderResponse, CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder")

        documentation {
            summary = "Creates a folder"
            description = """
                Creates a folder at a specified location.
                
                This folder will automatically create parent directories if needed. This request may fail half-way
                through and leave the file-system in an inconsistent state. It is up to the user to clean up this.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val updateAcl = call<FilesUpdateAclRequest, FilesUpdateAclResponse, CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")

        documentation {
            summary = "Updates the permissions of a file"
            description = """
                Updates the permissions of a file.
                
                Note that not all providers supports this endpoint. You can query ${docCallRef(FileCollections::browse)}
                or ${docCallRef(FileCollections::retrieve)} with the `includeSupport` flag. 
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }
        }
    }

    val trash = call<FilesTrashRequest, FilesTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash")

        documentation {
            summary = "Moves a file to the trash"
            description = """
                Moves a file to the trash.
                
                This operation acts as a non-permanent delete for users. Users will be able to restore the file from
                trash later, if needed. It is up to the provider to determine if the trash should be automatically
                deleted and where this trash should be stored.
                
                Note that not all providers supports this endpoint. You can query ${docCallRef(FileCollections::browse)}
                or ${docCallRef(FileCollections::retrieve)} with the `includeSupport` flag.
                
                This is a long running task. As a result, this operation might respond with a status code which indicate
                that it will continue in the background. Progress of this job can be followed using the task API.
            """.trimIndent()

            error {
                statusCode = HttpStatusCode.NotFound
                description = "Either the oldPath or newPath exists or you lack permissions"
            }

            error {
                statusCode = HttpStatusCode.Forbidden
                description = "You lack permissions to perform this operation"
            }

            error {
                statusCode = HttpStatusCode.BadRequest
                description = "This operation is not supported by the provider"
            }
        }
    }

    /*
    // TODO Interface tbd
    val search = call<FilesSearchRequest, FilesSearchResponse, CommonErrorMessage>("search") {
    }
     */
}
