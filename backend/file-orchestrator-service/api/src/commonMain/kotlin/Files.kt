package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceUpdate
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface WithConflictPolicy {
    val conflictPolicy: WriteConflictPolicy
}

interface WithPath {
    val id: String
}

interface WithPathMoving {
    val oldId: String
    val newId: String
}

@Serializable
data class FindByPath(override val id: String) : WithPath

@Serializable
sealed class LongRunningTask {
    @Serializable
    @SerialName("complete")
    class Complete : LongRunningTask()

    @Serializable
    @SerialName("continues_in_background")
    data class ContinuesInBackground(val taskId: String) : LongRunningTask()
}

@Serializable
enum class FilesSortBy {
    PATH,
    SIZE,
    MODIFIED_AT
}

typealias FilesMoveRequest = BulkRequest<FilesMoveRequestItem>

@Serializable
data class FilesMoveRequestItem(
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesMoveResponse = BulkResponse<LongRunningTask?>

typealias FilesCopyRequest = BulkRequest<FilesCopyRequestItem>

@Serializable
data class FilesCopyRequestItem(
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesCopyResponse = BulkResponse<LongRunningTask?>

typealias FilesDeleteRequest = BulkRequest<FindByPath>
typealias FilesDeleteResponse = BulkResponse<LongRunningTask>

typealias FilesCreateFolderRequest = BulkRequest<FilesCreateFolderRequestItem>

@Serializable
data class FilesCreateFolderRequestItem(
    override val id: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesCreateFolderResponse = BulkResponse<LongRunningTask?>

typealias FilesUpdateAclRequest = BulkRequest<FilesUpdateAclRequestItem>

@Serializable
data class FilesUpdateAclRequestItem(
    override val id: String,
    val newAcl: List<ResourceAclEntry>
) : WithPath
typealias FilesUpdateAclResponse = Unit

typealias FilesTrashRequest = BulkRequest<FindByPath>
typealias FilesTrashResponse = BulkResponse<LongRunningTask?>

typealias FilesCreateUploadRequest = BulkRequest<FilesCreateUploadRequestItem>

@Serializable
data class FilesCreateUploadRequestItem(
    override val id: String,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
) : WithPath
typealias FilesCreateUploadResponse = BulkResponse<FilesCreateUploadResponseItem?>

@Serializable
data class FilesCreateUploadResponseItem(
    var endpoint: String,
    val protocol: UploadProtocol,
    val token: String,
)

enum class UploadProtocol {
    CHUNKED
}

typealias FilesCreateDownloadRequest = BulkRequest<FilesCreateDownloadRequestItem>

@Serializable
data class FilesCreateDownloadRequestItem(override val id: String) : WithPath
typealias FilesCreateDownloadResponse = BulkResponse<FilesCreateDownloadResponseItem?>

@Serializable
data class FilesCreateDownloadResponseItem(var endpoint: String)

// ---

@UCloudApiStable
object Files : ResourceApi<UFile, UFileSpecification, ResourceUpdate, UFileIncludeFlags, UFileStatus, Product.Storage,
    FSSupport>("files") {
    override val typeInfo = ResourceTypeInfo<UFile, UFileSpecification, ResourceUpdate, UFileIncludeFlags,
        UFileStatus, Product.Storage, FSSupport>()

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

    private val renameFileUseCase = "rename_file"
    private val copyFileToSelfUseCase = "copy_file_to_self"
    override fun documentation() {
        useCase(
            renameFileUseCase,
            "Renaming a file",
            trigger = "User-initiated action, typically though the user-interface",
            preConditions = listOf(
                "A file present at /123/my/file",
                "The user has EDIT permissions on the file"
            ),
            postConditions = listOf(
                "The file is moved to /123/my/new_file"
            ),
            flow = {
                val user = actor("user", "An authenticated user")
                success(
                    move,
                    bulkRequestOf(
                        FilesMoveRequestItem("/123/my/file", "/123/my/new_file", WriteConflictPolicy.REJECT)
                    ),
                    FilesMoveResponse(listOf(LongRunningTask.Complete())),
                    user
                )
            }
        )

        useCase(
            copyFileToSelfUseCase,
            "Copying a file to itself",
            trigger = "Use-initiated action, typically through the user-interface",
            preConditions = listOf(
                "A file present at /123/my/file",
                "The user has EDIT permissions on the file",
                "The provider supports RENAME for conflict policies"
            ),
            postConditions = listOf(
                "A new file present at '/123/my/file (1)'"
            ),
            flow = {
                val user = actor("user", "An authenticated user")
                success(
                    copy,
                    bulkRequestOf(
                        FilesCopyRequestItem("/123/my/file", "/123/my/file", WriteConflictPolicy.RENAME)
                    ),
                    FilesMoveResponse(listOf(LongRunningTask.Complete())),
                    user
                )
            }
        )

        document(browse, UCloudApiDocC(
            """
                Browses the contents of a directory.
                
                The results will be returned using the standard pagination API of UCloud. Consistency is slightly
                relaxed for this endpoint as it is typically hard to enforce for filesystems. Provider's are heavily
                encouraged to try and find all files on the first request and return information about them in
                subsequent requests. For example, a client might list all file names in the initial request and use
                this list for all subsequent requests and retrieve additional information about the files. If the files
                no longer exist then the provider should simply not include these results.
            """
        ))

        document(retrieve, UCloudApiDocC(
            """
                Retrieves information about a single file.
                
                This file can be of any type. Clients can request additional information about the file using the
                `include*` flags of the request. Note that not all providers support all information. Clients can query
                this information using ${docCallRef(FileCollections::browse)} or 
                ${docCallRef(FileCollections::retrieve)} with the `includeSupport` flag.
            """
        ))

        document(delete, UCloudApiDocC(
            """
                Permanently deletes one or more files
                
                This call will recursively delete files if needed. It is possible that a provider might fail to
                completely delete the entire sub-tree. This can, for example, happen because of a crash or because the
                file-system is unable to delete a given file. This will lead the file-system in an inconsistent state.
                It is not guaranteed that the provider will be able to detect this error scenario. A client of the
                API can check if the file has been deleted by calling `retrieve` on the file.
            """
        ))
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

            responseExample(
                HttpStatusCode.BadRequest,
                "The operation couldn't be completed because of the write conflict policy"
            )

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )

            useCaseReference(renameFileUseCase, "Example of using `move` to rename a file")
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
                    
                    TODO What happens with metadata, acls and extended attributes?
                """.trimIndent()

            responseExample(
                HttpStatusCode.BadRequest,
                "The operation couldn't be completed because of the write conflict policy"
            )

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )

            useCaseReference(copyFileToSelfUseCase, "Example of duplicating a file")
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

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )
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

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )
        }
    }

    val createFolder = call<FilesCreateFolderRequest, FilesCreateFolderResponse, CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder")

        documentation {
            summary = "Creates a folder"
            description = """
                    Creates a folder at a specified location.
                    
                    This folder will automatically create parent directories if needed. This request may fail half-way
                    through and leave the file-system in an inconsistent state. It is up to the user to clean this up.
                """.trimIndent()

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )
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

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )

            responseExample(
                HttpStatusCode.BadRequest,
                "This operation is not supported by the provider"
            )
        }
    }

    override val create: Nothing? = null
    override val search: Nothing? = null
    override val delete get() = super.delete!!
}
