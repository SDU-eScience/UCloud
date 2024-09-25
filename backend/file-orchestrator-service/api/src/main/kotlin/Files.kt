package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.ChargeType
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.accounting.api.providers.SupportByProvider
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.debug.DebugSensitive
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdate
import dk.sdu.cloud.provider.api.Resources
import dk.sdu.cloud.task.api.BackgroundTask
import dk.sdu.cloud.task.api.TaskState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

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
@UCloudApiStable
data class FindByPath(override val id: String) : WithPath

@Serializable
@UCloudApiStable
enum class FilesSortBy {
    PATH,
    SIZE,
    MODIFIED_AT
}

@Serializable
@UCloudApiStable
enum class UploadType {
    FILE,
    FOLDER
}

typealias FilesMoveRequest = BulkRequest<FilesMoveRequestItem>

@Serializable
@UCloudApiStable
data class FilesMoveRequestItem(
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesMoveResponse = BulkResponse<BackgroundTask?>

typealias FilesCopyRequest = BulkRequest<FilesCopyRequestItem>

@Serializable
@UCloudApiStable
data class FilesCopyRequestItem(
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesCopyResponse = BulkResponse<BackgroundTask?>

typealias FilesDeleteRequest = BulkRequest<FindByPath>
typealias FilesDeleteResponse = BulkResponse<BackgroundTask>

typealias FilesCreateFolderRequest = BulkRequest<FilesCreateFolderRequestItem>

@Serializable
@UCloudApiStable
data class FilesCreateFolderRequestItem(
    override val id: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesCreateFolderResponse = BulkResponse<BackgroundTask?>

typealias FilesUpdateAclRequest = BulkRequest<FilesUpdateAclRequestItem>

@Serializable
@UCloudApiStable
data class FilesUpdateAclRequestItem(
    override val id: String,
    val newAcl: List<ResourceAclEntry>
) : WithPath
typealias FilesUpdateAclResponse = Unit

typealias FilesTrashRequest = BulkRequest<FindByPath>
typealias FilesTrashResponse = BulkResponse<BackgroundTask?>

typealias FilesCreateUploadRequest = BulkRequest<FilesCreateUploadRequestItem>

typealias FilesEmptyTrashRequest = BulkRequest<FindByPath>
typealias FilesEmptyTrashResponse = BulkResponse<BackgroundTask?>

@Serializable
@UCloudApiStable
data class FilesCreateUploadRequestItem(
    override val id: String,
    val type: UploadType,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
) : WithPath
typealias FilesCreateUploadResponse = BulkResponse<FilesCreateUploadResponseItem?>

@Serializable
@UCloudApiStable
data class FilesCreateUploadResponseItem(
    var endpoint: String,
    val protocol: UploadProtocol,
    val token: String,
) : DebugSensitive {
    override fun removeSensitiveInformation(): JsonElement = JsonNull
}

@UCloudApiStable
enum class UploadProtocol {
    CHUNKED,
    WEBSOCKET
}

typealias FilesCreateDownloadRequest = BulkRequest<FilesCreateDownloadRequestItem>

@Serializable
@UCloudApiStable
data class FilesCreateDownloadRequestItem(override val id: String) : WithPath
typealias FilesCreateDownloadResponse = BulkResponse<FilesCreateDownloadResponseItem?>

@Serializable
@UCloudApiStable
data class FilesCreateDownloadResponseItem(var endpoint: String) : DebugSensitive {
    override fun removeSensitiveInformation(): JsonElement = JsonNull
}

@Serializable
@UCloudApiOwnedBy(Files::class)
data class UFileUpdate(override val timestamp: Long, override val status: String?) : ResourceUpdate

@Serializable
@UCloudApiStable
data class FilesStreamingSearchRequest(
    val flags: UFileIncludeFlags,
    val query: String,
    val currentFolder: String? = null,
)

@Serializable
@UCloudApiStable
sealed class FilesStreamingSearchResult {
    @Serializable
    @SerialName("result")
    @UCloudApiStable
    data class Result(val batch: List<UFile>) : FilesStreamingSearchResult()
    @Serializable
    @SerialName("end_of_results")
    @UCloudApiStable
    class EndOfResults : FilesStreamingSearchResult()
}

@Serializable
data class FilesTransferRequest(
    val sourcePath: String,
    val destinationPath: String,
)

@Serializable
class FilesTransferResponse

// ---

@UCloudApiStable
object Files : ResourceApi<UFile, UFileSpecification, UFileUpdate, UFileIncludeFlags, UFileStatus, Product.Storage,
        FSSupport>("files") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        UFile.serializer(),
        typeOfIfPossible<UFile>(),
        UFileSpecification.serializer(),
        typeOfIfPossible<UFileSpecification>(),
        UFileUpdate.serializer(),
        typeOfIfPossible<UFileUpdate>(),
        UFileIncludeFlags.serializer(),
        typeOfIfPossible<UFileIncludeFlags>(),
        UFileStatus.serializer(),
        typeOfIfPossible<UFileStatus>(),
        FSSupport.serializer(),
        typeOfIfPossible<FSSupport>(),
        Product.Storage.serializer(),
        typeOfIfPossible<Product.Storage>(),
    )

    init {
        title = "Files"

        //language=markdown
        description = """Files in UCloud is a resource for storing, retrieving and organizing data in UCloud.

${Resources.readMeFirst}

The file-system of UCloud provide researchers with a way of storing large data-sets efficiently and securely. The
file-system is one of UCloud's core features and almost all other features, either directly or indirectly, interact
with it. For example:

- All interactions in UCloud (including files) are automatically [audited](/docs/developer-guide/core/monitoring/auditing.md)
- UCloud allows compute [`Jobs`](/docs/developer-guide/orchestration/compute/jobs.md) to consume UCloud files. Either
  through containerized workloads or virtual machines.
- Authorization and [project management](/docs/developer-guide/accounting-and-projects/projects/projects.md)
- Powerful [file metadata system](/docs/developer-guide/orchestration/storage/metadata/templates.md) for data management

A file in UCloud ($TYPE_REF UFile) closely follows the concept of a computer file you might already be familiar with.
The functionality of a file is mostly determined by its `type`. The two most important types are the
[`DIRECTORY`]($TYPE_REF_LINK FileType) and [`FILE`]($TYPE_REF_LINK FileType) types. A
[`DIRECTORY`]($TYPE_REF_LINK FileType) is a container of $TYPE_REF UFile s. A directory can itself contain more
directories, which leads to a natural tree-like structure. [`FILE`s]($TYPE_REF_LINK FileType), also referred to as a
regular files, are data records which each contain a series of bytes.

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`Files`](/docs/developer-guide/orchestration/storage/files.md) | [`FilesProvider`](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md) | [`FilesControl`](/docs/developer-guide/orchestration/storage/providers/files/outgoing.md) |

---
"""
    }

    private const val renameFileUseCase = "rename_file"
    private const val copyFileToSelfUseCase = "copy_file_to_self"
    private const val uploadUseCase = "upload"
    private const val downloadUseCase = "download"
    private const val createFolderUseCase = "create_folder"
    private const val moveToTrashUseCase = "move_to_trash"
    private const val emptyingTrash = "empty_trash_folder"
    private const val browseUseCase = "browse"
    private const val retrieveUseCase = "retrieve"
    private const val deleteUseCase = "delete"
    private const val retrieveProductsUseCase = "retrieve_products"

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
                    FilesMoveResponse(
                        listOf(
                            BackgroundTask(
                                taskId = 1,
                                10L,
                                10L,
                                "user",
                                provider = "K8",
                                status = BackgroundTask.Status(
                                    TaskState.SUCCESS,
                                    "Moving File",
                                    null,
                                    "Done",
                                    100.0,
                                ),
                                BackgroundTask.Specification(
                                    canPause = true,
                                    canCancel = false
                                ),
                                "folder"
                            )
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            copyFileToSelfUseCase,
            "Copying a file to itself",
            trigger = "User-initiated action, typically through the user-interface",
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
                    FilesMoveResponse(
                        listOf(
                            BackgroundTask(
                                taskId = 2,
                                10L,
                                10L,
                                "user",
                                provider = "K8",
                                status = BackgroundTask.Status(
                                    TaskState.SUCCESS,
                                    "Copying File",
                                    null,
                                    "Done",
                                    100.0,
                                ),
                                BackgroundTask.Specification(
                                    canPause = false,
                                    canCancel = false
                                ),
                                "copy"
                            )
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            uploadUseCase,
            "Uploading a file",
            trigger = "User initiated",
            preConditions = listOf(
                "A folder at '/123/folder'",
                "The user has EDIT permissions on the file",
                "The provider supports the CHUNKED protocol"
            ),
            postConditions = listOf(
                "A new file present at '/123/folder/file'"
            ),
            flow = {
                val user = basicUser()
                success(
                    createUpload,
                    bulkRequestOf(
                        FilesCreateUploadRequestItem(
                            "/123/folder",
                            UploadType.FILE,
                            listOf(UploadProtocol.CHUNKED),
                            WriteConflictPolicy.REJECT
                        )
                    ),
                    FilesCreateUploadResponse(
                        listOf(
                            FilesCreateUploadResponseItem(
                                "https://provider.example.com/ucloud/example-provider/chunked",
                                UploadProtocol.CHUNKED,
                                "f1460d47e583653f7723204e5ff3f50bad91a658"
                            )
                        )
                    ),
                    user
                )

                comment("The user can now proceed to upload using the chunked protocol at the provided endpoint")
            }
        )

        useCase(
            downloadUseCase,
            "Downloading a file",
            trigger = "User initiated",
            preConditions = listOf(
                "A file at '/123/folder/file",
                "The user has READ permissions on the file"
            ),
            flow = {
                val user = basicUser()
                success(
                    createDownload,
                    bulkRequestOf(
                        FilesCreateDownloadRequestItem("/123/folder/file")
                    ),
                    BulkResponse(
                        listOf(
                            FilesCreateDownloadResponseItem(
                                "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069"
                            )
                        )
                    ),
                    user
                )

                comment("The user can now download the file through normal HTTP(s) GET at the provided endpoint")
            }
        )

        useCase(
            createFolderUseCase,
            "Creating a folder",
            trigger = "User initiated",
            preConditions = listOf(
                "A folder at '/123/folder",
                "The user has EDIT permissions on the file"
            ),
            postConditions = listOf(
                "A new file exists at '/123/folder/a"
            ),
            flow = {
                val user = basicUser()
                success(
                    createFolder,
                    bulkRequestOf(
                        FilesCreateFolderRequestItem(
                            "/123/folder/a",
                            WriteConflictPolicy.REJECT
                        )
                    ),
                    FilesCreateFolderResponse(
                        listOf(
                            BackgroundTask(
                                taskId = 1,
                                10L,
                                10L,
                                "user",
                                provider = "K8",
                                status = BackgroundTask.Status(
                                    TaskState.SUCCESS,
                                    "Creating Folder",
                                    null,
                                    "Done",
                                    100.0,
                                ),
                                BackgroundTask.Specification(
                                    canPause = false,
                                    canCancel = false
                                ),
                                "folder"
                            )
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            moveToTrashUseCase,
            "Moving multiple files to trash",
            trigger = "User initiated",
            preConditions = listOf(
                "A folder at '/123/folder'",
                "A file at '/123/file'",
                "The user has EDIT permissions for all files involved",
            ),
            postConditions = listOf(
                "The folder and all children are moved to the provider's trash folder",
                "The file is moved to the provider's trash folder"
            ),
            flow = {
                val user = basicUser()
                success(
                    trash,
                    bulkRequestOf(
                        FindByPath("/123/folder"),
                        FindByPath("/123/file")
                    ),
                    BulkResponse(
                        listOf(
                            BackgroundTask(
                                taskId = 1,
                                10L,
                                10L,
                                "user",
                                provider = "K8",
                                status = BackgroundTask.Status(
                                    TaskState.SUCCESS,
                                    "Moving Folder To Trash",
                                    null,
                                    "Done",
                                    100.0,
                                ),
                                BackgroundTask.Specification(
                                    canPause = false,
                                    canCancel = true
                                ),
                                "move"
                            ),
                            BackgroundTask(
                                taskId = 2,
                            10L,
                            10L,
                            "user",
                                provider = "K8",
                            status = BackgroundTask.Status(
                                TaskState.SUCCESS,
                                "Moving File to Trash",
                                null,
                                "Done",
                                100.0,
                            ),
                            BackgroundTask.Specification(
                                canPause = false,
                                canCancel = true
                            ),
                                "trash"

                            )
                        )
                    ),
                    user
                )
            }
        )

        useCase(
            emptyingTrash,
            "Emptying trash folder",
            trigger = "User initiated",
            preConditions = listOf(
                "A trash folder located at /home/trash",
                "The trash folder contains two files and a folder"
            ),
            postConditions = listOf(
                "The folder and all children are removed from the trash folder",
                "The files is removed from the trash folder"
            ),
            flow = {
                val user = basicUser()
                success(
                    trash,
                    bulkRequestOf(
                        FindByPath("/home/trash")
                    ),
                    BulkResponse(
                        listOf(
                            BackgroundTask(
                                taskId = 1,
                                10L,
                                10L,
                                "user",
                                provider = "K8",
                                status = BackgroundTask.Status(
                                    TaskState.SUCCESS,
                                    "Emptying Trash",
                                    null,
                                    "Done",
                                    100.0,
                                ),
                                BackgroundTask.Specification(
                                    canPause = true,
                                    canCancel = false
                                ),
                                "trash"
                            )
                        )
                    ),
                    user
                )
            }
        )


        useCase(
            browseUseCase,
            "Browsing the contents of a folder",
            trigger = "User initiated",
            preConditions = listOf(
                "A folder at '/123/folder",
                "The user has READ permissions on the file"
            ),
            flow = {
                val user = basicUser()
                success(
                    browse,
                    ResourceBrowseRequest(
                        UFileIncludeFlags(includeTimestamps = true)
                    ),
                    PageV2(
                        50,
                        listOf(
                            UFile(
                                "/123/folder/file.txt",
                                UFileSpecification(
                                    "123",
                                    ProductReference("u1-cephfs", "u1-cephfs", "ucloud")
                                ),
                                1632903417165,
                                UFileStatus(
                                    modifiedAt = 1632903417165
                                ),
                                ResourceOwner("user", "f63919cd-60d3-45d3-926b-0246dcc697fd")
                            )
                        ),
                        null
                    ),
                    user
                )
            }
        )

        useCase(
            retrieveUseCase,
            "Retrieving a single file",
            trigger = "User initiated",
            preConditions = listOf(
                "A file at '/123/folder",
                "The user has READ permissions on the file"
            ),
            flow = {
                val user = basicUser()
                success(
                    retrieve,
                    ResourceRetrieveRequest(
                        UFileIncludeFlags(includeTimestamps = true),
                        "/123/folder"
                    ),
                    UFile(
                        "/123/folder",
                        UFileSpecification(
                            "123",
                            ProductReference("u1-cephfs", "u1-cephfs", "ucloud")
                        ),
                        1632903417165,
                        UFileStatus(
                            type = FileType.DIRECTORY,
                            modifiedAt = 1632903417165
                        ),
                        ResourceOwner("user", "f63919cd-60d3-45d3-926b-0246dcc697fd")
                    ),
                    user
                )
            }
        )

        useCase(
            deleteUseCase,
            "Deleting a file permanently",
            trigger = "User initiated",
            preConditions = listOf(
                "A file at '/123/folder",
                "The user has EDIT permissions on the file"
            ),
            flow = {
                val user = basicUser()
                success(
                    delete,
                    bulkRequestOf(
                        FindByStringId("/123/folder")
                    ),
                    BulkResponse(listOf(Unit)),
                    user
                )
            }
        )

        useCase(
            retrieveProductsUseCase,
            "Retrieving a list of products supported by accessible providers",
            trigger = "Typically triggered by a client to determine which operations are supported",
            preConditions = listOf(
                "The user has access to the 'ucloud' provider"
            ),
            flow = {
                val user = basicUser()
                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "ucloud" to listOf(
                                ResolvedSupport<Product.Storage, FSSupport>(
                                    Product.Storage(
                                        "u1-cephfs",
                                        1L,
                                        ProductCategoryId("u1-cephfs", "ucloud"),
                                        "Storage provided by UCloud",
                                        unitOfPrice = ProductPriceUnit.PER_UNIT,
                                        chargeType = ChargeType.DIFFERENTIAL_QUOTA
                                    ),
                                    FSSupport(
                                        ProductReference("u1-cephfs", "u1-cephfs", "ucloud"),
                                        FSProductStatsSupport(
                                            sizeInBytes = true,
                                            sizeIncludingChildrenInBytes = true,
                                            modifiedAt = true,
                                            createdAt = true,
                                            accessedAt = false,
                                            unixPermissions = true,
                                            unixOwner = true,
                                            unixGroup = true
                                        ),
                                        FSCollectionSupport(
                                            aclModifiable = false,
                                            usersCanCreate = true,
                                            usersCanDelete = true,
                                            usersCanRename = true
                                        ),
                                        FSFileSupport(
                                            aclModifiable = false,
                                            trashSupported = true,
                                            isReadOnly = false,
                                            sharesSupported = true
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    user
                )
            }
        )

        document(
            browse, UCloudApiDocC(
                """
                Browses the contents of a directory.
                
                The results will be returned using the standard pagination API of UCloud. Consistency is slightly
                relaxed for this endpoint as it is typically hard to enforce for filesystems. Provider's are heavily
                encouraged to try and find all files on the first request and return information about them in
                subsequent requests. For example, a client might list all file names in the initial request and use
                this list for all subsequent requests and retrieve additional information about the files. If the files
                no longer exist then the provider should simply not include these results.
            """
            )
        )

        document(
            retrieve, UCloudApiDocC(
                """
                Retrieves information about a single file.
                
                This file can be of any type. Clients can request additional information about the file using the
                `include*` flags of the request. Note that not all providers support all information. Clients can query
                this information using ${docCallRef(FileCollections::browse)} or 
                ${docCallRef(FileCollections::retrieve)} with the `includeSupport` flag.
            """
            )
        )

        document(
            delete, UCloudApiDocC(
                """
                Permanently deletes one or more files
                
                This call will recursively delete files if needed. It is possible that a provider might fail to
                completely delete the entire sub-tree. This can, for example, happen because of a crash or because the
                file-system is unable to delete a given file. This will lead the file-system in an inconsistent state.
                It is not guaranteed that the provider will be able to detect this error scenario. A client of the
                API can check if the file has been deleted by calling `retrieve` on the file.
            """
            )
        )

        document(updateAcl, UCloudApiDocC(
            """
            Updates the ACL of a single file.
                
            ---
            
            __⚠️ WARNING:__ No providers currently support this API. Instead use the
            $CALL_REF files.collections.updateAcl endpoint.
            
            ---
            """
        ))
    }

    val move = call("move", BulkRequest.serializer(FilesMoveRequestItem.serializer()), BulkResponse.serializer(BackgroundTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "move")

        documentation {
            summary = "Move a file from one path to another"
            description = """
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

    val copy = call("copy", BulkRequest.serializer(FilesCopyRequestItem.serializer()), BulkResponse.serializer(BackgroundTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "copy")

        documentation {
            summary = "Copies a file from one path to another"
            description = """
                The file can be of any type. If a directory is chosen then this will recursively copy all of its
                children. This request might fail half-way through. This can potentially lead to a situation where
                a partial file is left on the file-system. It is left to the user to clean up this file.
                
                This operation handles conflicts depending on the supplied `WriteConflictPolicy`.
                
                This is a long running task. As a result, this operation might respond with a status code which
                indicate that it will continue in the background. Progress of this job can be followed using the
                task API.
                
                UCloud applied metadata will not be copied to the new file. File-system metadata (e.g.
                extended-attributes) may be moved, however this is provider dependant.
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

    val createUpload = call("createUpload", BulkRequest.serializer(FilesCreateUploadRequestItem.serializer()), BulkResponse.serializer(FilesCreateUploadResponseItem.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "upload")

        documentation {
            summary = "Creates an upload session between the user and the provider"
            description = """
                An upload can be either a file or folder, if supported by the provider, and depending on the
                $TYPE_REF UploadTypespecified in the request. The desired path and a list of supported $TYPE_REF UploadProtocol s 
                are also specified in the request. The latter is used by the provider to negotiate which protocol to use.
                
                The response will contain an endpoint which is ready to accept the upload, as well as the chosen
                $TYPE_REF UploadProtocol and a unique token.
                
                At the time of writing the default and preferred protocol is $TYPE_REF UploadProtocol.WEBSOCKET .
            """.trimIndent()

            responseExample(
                HttpStatusCode.NotFound,
                "Either the oldPath or newPath exists or you lack permissions"
            )

            responseExample(
                HttpStatusCode.Forbidden,
                "You lack permissions to perform this operation"
            )

            useCaseReference(uploadUseCase, "Uploading a file with the chunked protocol")
        }
    }

    val createDownload = call("createDownload", BulkRequest.serializer(FilesCreateDownloadRequestItem.serializer()), BulkResponse.serializer(FilesCreateDownloadResponseItem.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "download")

        documentation {
            summary = "Creates a download session between the user and the provider"
            description = """
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

            useCaseReference(downloadUseCase, "Downloading a file")
        }
    }

    val createFolder = call("createFolder", BulkRequest.serializer(FilesCreateFolderRequestItem.serializer()), BulkResponse.serializer(BackgroundTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "folder")

        documentation {
            summary = "Creates one or more folders"
            description = """
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

            useCaseReference(createFolderUseCase, "Creating a folder")
        }
    }

    val trash = call("trash", BulkRequest.serializer(FindByPath.serializer()), BulkResponse.serializer(BackgroundTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "trash")

        documentation {
            summary = "Moves a file to the trash"
            description = """
                    This operation acts as a non-permanent delete for users. Users will be able to restore the file from
                    trash later, if needed. It is up to the provider to determine if the trash should be automatically
                    deleted and where this trash should be stored.
                    
                    Not all providers supports this endpoint. You can query $CALL_REF files.collections.browse
                    or $CALL_REF files.collections.retrieve with the `includeSupport` flag.
                    
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

            useCaseReference(moveToTrashUseCase, "Moving files to trash")
        }
    }

    val emptyTrash = call("emptyTrash", BulkRequest.serializer(FindByPath.serializer()), BulkResponse.serializer(BackgroundTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "emptyTrash")

        documentation {
            summary = "Permanently deletes all files from the selected trash folder thereby emptying it"
            description = """
                    This operation acts as a permanent delete for users. Users will NOT be able to restore the file 
                    later, if needed. 
                    
                    Not all providers supports this endpoint. You can query $CALL_REF files.collections.browse
                    or $CALL_REF files.collections.retrieve with the `includeSupport` flag.
                    
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

            useCaseReference(emptyingTrash, "Moving files to trash")
        }
    }

    val streamingSearch = call("streamingSearch", FilesStreamingSearchRequest.serializer(), FilesStreamingSearchResult.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.END_USER
        }

        websocket(baseContext)

        documentation {
            summary = "Searches through the files of a user in all accessible files"
            description = """
                This endpoint uses a specialized API for returning search results in a streaming fashion. In all other
                ways, this endpoint is identical to the normal search API.
                
                This endpoint can be used instead of the normal search API as it will contact providers using the
                non-streaming version if they do not support it. In such a case, the core will retrieve multiple pages
                in order to stream in more content.
                
                Clients should expect that this endpoint stops returning results after a given timeout. After which,
                it is no longer possible to request additional results. 
            """.trimIndent()
        }
    }

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    val transfer = call(
        "transfer",
        BulkRequest.serializer(FilesTransferRequest.serializer()),
        BulkResponse.serializer(FilesTransferResponse.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(baseContext, "transfer", roles = Roles.END_USER)

        documentation {
            summary = "Transfers files between two different service providers"
        }
    }

    override val create: Nothing? = null
    override val search get() = super.search!!
    override val delete get() = super.delete!!
}
