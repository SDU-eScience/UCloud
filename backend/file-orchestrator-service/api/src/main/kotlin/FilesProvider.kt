package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiDoc("A partial UFile returned by providers and made complete by UCloud/Core")
data class PartialUFile(
    @UCloudApiDoc("The id of the file. Corresponds to UFile.id")
    val id: String,
    @UCloudApiDoc("The status of the file. Corresponds to UFile.status")
    val status: UFileStatus,
    @UCloudApiDoc("The creation timestamp. Corresponds to UFile.createdAt")
    val createdAt: Long,
    @UCloudApiDoc("The owner of the file. Corresponds to UFile.owner. This will default to the collection's owner.")
    val owner: ResourceOwner? = null,
    @UCloudApiDoc("The permissions of the file. Corresponds to UFile.permissions." +
        "This will default to the collection's permissions.")
    val permissions: ResourcePermissions? = null,
    @UCloudApiDoc("Legacy for reading old sensitivity values stored on in extended attributes")
    val legacySensitivity: String? = null
)

@Serializable
data class FilesProviderBrowseRequest(
    val resolvedCollection: FileCollection,
    val browse: ResourceBrowseRequest<UFileIncludeFlags>
)
typealias FilesProviderBrowseResponse = PageV2<PartialUFile>

@Serializable
data class FilesProviderRetrieveRequest(
    val resolvedCollection: FileCollection,
    val retrieve: ResourceRetrieveRequest<UFileIncludeFlags>
)
typealias FilesProviderRetrieveResponse = PartialUFile

@Serializable
data class FilesProviderMoveRequestItem(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesProviderMoveResponse = FilesMoveResponse

@Serializable
data class FilesProviderCopyRequestItem(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    override val oldId: String,
    override val newId: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesProviderCopyResponse = FilesCopyResponse

@Serializable
data class FilesProviderCreateFolderRequestItem(
    val resolvedCollection: FileCollection,
    override val id: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesProviderCreateFolderResponse = FilesCreateFolderResponse

@Serializable
data class FilesProviderTrashRequestItem(
    val resolvedCollection: FileCollection,
    override val id: String
) : WithPath
typealias FilesProviderTrashResponse = FilesTrashResponse

@Serializable
data class FilesProviderEmptyTrashRequestItem(
    val resolvedCollection: FileCollection,
    override val id: String
) : WithPath
typealias FilesProviderEmptyTrashResponse = FilesTrashResponse


@Serializable
data class FilesProviderCreateDownloadRequestItem(
    val resolvedCollection: FileCollection,
    override val id: String
) : WithPath
typealias FilesProviderCreateDownloadResponse = FilesCreateDownloadResponse

@Serializable
data class FilesProviderCreateUploadRequestItem(
    val resolvedCollection: FileCollection,
    override val id: String,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
) : WithPath
typealias FilesProviderCreateUploadResponse = FilesCreateUploadResponse

@Serializable
data class FilesProviderSearchRequest(
    val query: String,
    val owner: ResourceOwner,
    val flags: UFileIncludeFlags,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

@Serializable
data class FilesProviderStreamingSearchRequest(
    val query: String,
    val owner: ResourceOwner,
    val flags: UFileIncludeFlags,
    val currentFolder: String? = null,
)

@Serializable
sealed class FilesProviderStreamingSearchResult {
    @Serializable
    @SerialName("result")
    data class Result(val batch: List<PartialUFile>) : FilesProviderStreamingSearchResult()

    @Serializable
    @SerialName("end_of_results")
    class EndOfResults : FilesProviderStreamingSearchResult()
}

open class FilesProvider(provider: String) : ResourceProviderApi<UFile, UFileSpecification, UFileUpdate,
    UFileIncludeFlags, UFileStatus, Product.Storage, FSSupport>("files", provider) {
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

    val browse = call("browse", FilesProviderBrowseRequest.serializer(), PageV2.serializer(PartialUFile.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "browse", roles = Roles.SERVICE) // TODO FIXME
    }

    val retrieve = call("retrieve", FilesProviderRetrieveRequest.serializer(), FilesProviderRetrieveResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "retrieve", roles = Roles.SERVICE) // TODO FIXME
    }

    val move = call("move", BulkRequest.serializer(FilesProviderMoveRequestItem.serializer()), BulkResponse.serializer(LongRunningTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "move", roles = Roles.SERVICE)
    }

    val copy = call("copy", BulkRequest.serializer(FilesProviderCopyRequestItem.serializer()), BulkResponse.serializer(LongRunningTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "copy", roles = Roles.SERVICE)
    }

    val createFolder = call("createFolder", BulkRequest.serializer(FilesProviderCreateFolderRequestItem.serializer()), BulkResponse.serializer(LongRunningTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "folder", roles = Roles.SERVICE)
    }

    val trash = call("trash", BulkRequest.serializer(FilesProviderTrashRequestItem.serializer()), BulkResponse.serializer(LongRunningTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "trash", roles = Roles.SERVICE)
    }

    val emptyTrash = call("emptyTrash", BulkRequest.serializer(FilesProviderEmptyTrashRequestItem.serializer()), BulkResponse.serializer(LongRunningTask.serializer().nullable), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "emptyTrash", roles = Roles.SERVICE)
    }

    val createUpload = call("createUpload", BulkRequest.serializer(FilesProviderCreateUploadRequestItem.serializer()), BulkResponse.serializer(FilesCreateUploadResponseItem.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "upload", roles = Roles.SERVICE)
    }

    val createDownload = call("createDownload", BulkRequest.serializer(FilesProviderCreateDownloadRequestItem.serializer()), BulkResponse.serializer(FilesCreateDownloadResponseItem.serializer().nullable), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, "download", roles = Roles.SERVICE)
    }

    val search = call("search", FilesProviderSearchRequest.serializer(), PageV2.serializer(PartialUFile.serializer()), CommonErrorMessage.serializer()) {
        httpSearch(baseContext, roles = Roles.SERVICE)
    }

    val streamingSearch = call("streamingSearch", FilesProviderStreamingSearchRequest.serializer(), FilesProviderStreamingSearchResult.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
            roles = Roles.SERVICE
        }

        websocket(baseContext)
    }

    override val delete get() = super.delete!!
}
