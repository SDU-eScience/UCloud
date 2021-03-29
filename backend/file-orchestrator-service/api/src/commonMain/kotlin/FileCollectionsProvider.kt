package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import kotlinx.serialization.Serializable

// ---

@Serializable
data class ProxiedRequest<T>(
    val username: String,
    val request: T
)

@Serializable
data class FileCollectionsProviderBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias FileCollectionsProviderBrowseResponse = PageV2<FileCollection>

typealias FileCollectionsProviderRetrieveRequest = ProxiedRequest<FindByStringId>
typealias FileCollectionsProviderRetrieveResponse = FileCollection

typealias FileCollectionsProviderCreateRequest = ProxiedRequest<BulkRequest<FileCollection>>
typealias FileCollectionsProviderCreateResponse = BulkResponse<FindByStringId>

typealias FileCollectionsProviderDeleteRequest = ProxiedRequest<BulkRequest<FindByStringId>>
typealias FileCollectionsProviderDeleteResponse = Unit

typealias FileCollectionsProviderRenameRequest = ProxiedRequest<BulkRequest<FileCollectionsProviderRenameRequestItem>>

@Serializable
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsProviderRenameResponse = Unit

typealias FileCollectionsProviderUpdateAclRequest = ProxiedRequest<BulkRequest<FileCollectionsProviderUpdateAclRequestItem>>

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

    val browse = call<ProxiedRequest<FileCollectionsProviderBrowseRequest>, FileCollectionsProviderBrowseResponse,
        CommonErrorMessage>("browse") {
        httpBrowse(baseContext, roles = Roles.SERVICE)
    }

    val retrieve = call<FileCollectionsProviderRetrieveRequest, FileCollectionsProviderRetrieveResponse,
        CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.SERVICE)
    }

    val retrieveManifest = call<FileCollectionsProviderRetrieveManifestRequest,
        FileCollectionsProviderRetrieveManifestResponse, CommonErrorMessage>("retrieveManifest") {
        httpRetrieve(baseContext, "manifest", roles = Roles.SERVICE)
    }

    val create = call<FileCollectionsProviderCreateRequest, FileCollectionsProviderCreateResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.SERVICE)
    }

    val delete = call<FileCollectionsProviderDeleteRequest, FileCollectionsProviderDeleteResponse,
        CommonErrorMessage>("delete") {
        httpDelete(baseContext, roles = Roles.SERVICE)
    }

    val rename = call<FileCollectionsProviderRenameRequest, FileCollectionsProviderRenameResponse,
        CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename", roles = Roles.SERVICE)
    }

    val updateAcl = call<FileCollectionsProviderUpdateAclRequest, FileCollectionsProviderUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl", roles = Roles.SERVICE)
    }
}
