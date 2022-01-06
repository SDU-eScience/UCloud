package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

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

open class FilesProvider(provider: String) : ResourceProviderApi<UFile, UFileSpecification, UFileUpdate,
    UFileIncludeFlags, UFileStatus, Product.Storage, FSSupport>("files", provider) {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        UFile.serializer(),
        typeOf<UFile>(),
        UFileSpecification.serializer(),
        typeOf<UFileSpecification>(),
        UFileUpdate.serializer(),
        typeOf<UFileUpdate>(),
        UFileIncludeFlags.serializer(),
        typeOf<UFileIncludeFlags>(),
        UFileStatus.serializer(),
        typeOf<UFileStatus>(),
        FSSupport.serializer(),
        typeOf<FSSupport>(),
        Product.Storage.serializer(),
        typeOf<Product.Storage>(),
    )

    val browse = call<FilesProviderBrowseRequest, FilesProviderBrowseResponse, CommonErrorMessage>("browse") {
        httpUpdate(baseContext, "browse", roles = Roles.SERVICE) // TODO FIXME
    }

    val retrieve = call<FilesProviderRetrieveRequest, FilesProviderRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpUpdate(baseContext, "retrieve", roles = Roles.SERVICE) // TODO FIXME
    }

    val move = call<BulkRequest<FilesProviderMoveRequestItem>, FilesProviderMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move", roles = Roles.SERVICE)
    }

    val copy = call<BulkRequest<FilesProviderCopyRequestItem>, FilesProviderCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy", roles = Roles.SERVICE)
    }

    val createFolder = call<BulkRequest<FilesProviderCreateFolderRequestItem>, FilesProviderCreateFolderResponse,
        CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder", roles = Roles.SERVICE)
    }

    val trash = call<BulkRequest<FilesProviderTrashRequestItem>, FilesProviderTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash", roles = Roles.SERVICE)
    }

    val emptyTrash = call<BulkRequest<FilesProviderEmptyTrashRequestItem>, FilesProviderEmptyTrashResponse, CommonErrorMessage>("emptyTrash") {
        httpUpdate(baseContext, "emptyTrash", roles = Roles.SERVICE)
    }

    val createUpload = call<BulkRequest<FilesProviderCreateUploadRequestItem>, FilesProviderCreateUploadResponse,
        CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload", roles = Roles.SERVICE)
    }

    val createDownload = call<BulkRequest<FilesProviderCreateDownloadRequestItem>, FilesProviderCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download", roles = Roles.SERVICE)
    }

    val search = call<FilesProviderSearchRequest, PageV2<PartialUFile>, CommonErrorMessage>("search") {
        httpSearch(baseContext, roles = Roles.SERVICE)
    }

    override val delete get() = super.delete!!
}
