package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceUpdate
import kotlinx.serialization.Serializable

// ---

@Serializable
data class FilesProviderBrowseRequest(
    val resolvedCollection: FileCollection,
    val browse: ResourceBrowseRequest<UFileIncludeFlags>
)
typealias FilesProviderBrowseResponse = PageV2<UFile>

@Serializable
data class FilesProviderRetrieveRequest(
    val resolvedCollection: FileCollection,
    val retrieve: ResourceRetrieveRequest<UFileIncludeFlags>
)
typealias FilesProviderRetrieveResponse = UFile

@Serializable
data class FilesProviderMoveRequest(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    override val oldPath: String,
    override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving, WithConflictPolicy
typealias FilesProviderMoveResponse = FilesMoveResponse

@Serializable
data class FilesProviderCopyRequest(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    override val oldPath: String,
    override val newPath: String,
    override val conflictPolicy: WriteConflictPolicy
) : WithPathMoving, WithConflictPolicy
typealias FilesProviderCopyResponse = FilesCopyResponse

@Serializable
data class FilesProviderDeleteRequestItem(
    val resolvedCollection: FileCollection,
    override val path: String
) : WithPath
typealias FilesProviderDeleteResponse = FilesDeleteResponse

@Serializable
data class FilesProviderCreateFolderRequestItem(
    val resolvedCollection: FileCollection,
    override val path: String,
    override val conflictPolicy: WriteConflictPolicy,
) : WithPath, WithConflictPolicy
typealias FilesProviderCreateFolderResponse = FilesCreateFolderResponse

@Serializable
data class FilesProviderUpdateAclRequestItem(
    val resolvedCollection: FileCollection,
    override val path: String,
    val newAcl: List<ResourceAclEntry>
) : WithPath
typealias FilesProviderUpdateAclResponse = FilesUpdateAclResponse

@Serializable
data class FilesProviderTrashRequest(
    val resolvedCollection: FileCollection,
    override val path: String
) : WithPath
typealias FilesProviderTrashResponse = FilesTrashResponse

@Serializable
data class FilesProviderCreateDownloadRequestItem(
    val resolvedCollection: FileCollection,
    override val path: String
) : WithPath
typealias FilesProviderCreateDownloadResponse = FilesCreateDownloadResponse

@Serializable
data class FilesProviderCreateUploadRequestItem(
    val resolvedCollection: FileCollection,
    override val path: String,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
) : WithPath
typealias FilesProviderCreateUploadResponse = FilesCreateUploadResponse

// ---

open class FilesProvider(provider: String) : ResourceProviderApi<UFile, UFileSpecification, ResourceUpdate,
    UFileIncludeFlags, UFileStatus, Product.Storage, FSSupport>("files", provider) {
    override val typeInfo = ResourceTypeInfo<UFile, UFileSpecification, ResourceUpdate, UFileIncludeFlags,
        UFileStatus, Product.Storage, FSSupport>()

    val browse = call<FilesProviderBrowseRequest, FilesProviderBrowseResponse, CommonErrorMessage>("browse") {
        httpUpdate(baseContext, "browse", roles = Roles.SERVICE) // TODO FIXME
    }

    val retrieve = call<FilesProviderRetrieveRequest, FilesProviderRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpUpdate(baseContext, "retrieve", roles = Roles.SERVICE) // TODO FIXME
    }

    val move = call<FilesProviderMoveRequest, FilesProviderMoveResponse, CommonErrorMessage>("move") {
        httpUpdate(baseContext, "move", roles = Roles.SERVICE)
    }

    val copy = call<FilesProviderCopyRequest, FilesProviderCopyResponse, CommonErrorMessage>("copy") {
        httpUpdate(baseContext, "copy", roles = Roles.SERVICE)
    }

    val createFolder = call<BulkRequest<FilesProviderCreateFolderRequestItem>, FilesProviderCreateFolderResponse,
        CommonErrorMessage>("createFolder") {
        httpCreate(baseContext, "folder", roles = Roles.SERVICE)
    }

    val trash = call<FilesProviderTrashRequest, FilesProviderTrashResponse, CommonErrorMessage>("trash") {
        httpUpdate(baseContext, "trash", roles = Roles.SERVICE)
    }

    val createUpload = call<BulkRequest<FilesProviderCreateUploadRequestItem>, FilesProviderCreateUploadResponse,
        CommonErrorMessage>("createUpload") {
        httpCreate(baseContext, "upload", roles = Roles.SERVICE)
    }

    val createDownload = call<BulkRequest<FilesProviderCreateDownloadRequestItem>, FilesProviderCreateDownloadResponse,
        CommonErrorMessage>("createDownload") {
        httpCreate(baseContext, "download", roles = Roles.SERVICE)
    }
}
