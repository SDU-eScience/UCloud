package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import kotlinx.serialization.Serializable

// ---

@Serializable
data class FileCollectionsProviderBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias FileCollectionsProviderBrowseResponse = PageV2<FileCollection>

typealias FileCollectionsProviderRetrieveRequest = FindByStringId
typealias FileCollectionsProviderRetrieveResponse = FileCollection

typealias FileCollectionsProviderCreateRequest = BulkRequest<FileCollection>
typealias FileCollectionsProviderCreateResponse = BulkResponse<FindByStringId>

typealias FileCollectionsProviderDeleteRequest = BulkRequest<FindByStringId>
typealias FileCollectionsProviderDeleteResponse = Unit

typealias FileCollectionsProviderRenameRequest = BulkRequest<FileCollectionsProviderRenameRequestItem>

@Serializable
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsProviderRenameResponse = Unit

typealias FileCollectionsProviderUpdateAclRequest = BulkRequest<FileCollectionsProviderUpdateAclRequestItem>

@Serializable
data class FileCollectionsProviderUpdateAclRequestItem(
    val id: String,
    val newAcl: List<ResourceAclEntry<FilePermission>>,
)
typealias FileCollectionsProviderUpdateAclResponse = Unit

typealias FileCollectionsProviderRetrieveManifestRequest = Unit
@Serializable
data class FileCollectionsProviderRetrieveManifestResponse(val support: List<FSSupport>)

// ---

open class FileCollectionsProvider(
    namespace: String,
) : CallDescriptionContainer("files.collections.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/files/collections"

    val browse = call<FileCollectionsProviderBrowseRequest, FileCollectionsProviderBrowseResponse,
        CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val retrieve = call<FileCollectionsProviderRetrieveRequest, FileCollectionsProviderRetrieveResponse,
        CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val retrieveManifest = call<FileCollectionsProviderRetrieveManifestRequest,
        FileCollectionsProviderRetrieveManifestResponse, CommonErrorMessage>("retrieveManifest") {
        httpRetrieve(baseContext, "manifest")
    }

    val create = call<FileCollectionsProviderCreateRequest, FileCollectionsProviderCreateResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val delete = call<FileCollectionsProviderDeleteRequest, FileCollectionsProviderDeleteResponse,
        CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val rename = call<FileCollectionsProviderRenameRequest, FileCollectionsProviderRenameResponse,
        CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename")
    }

    val updateAcl = call<FileCollectionsProviderUpdateAclRequest, FileCollectionsProviderUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }
}
