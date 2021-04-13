package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import kotlinx.serialization.Serializable

// ---

@Serializable
data class FileCollectionsBrowseRequest(
    val provider: String,
    override val includeSupport: Boolean? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2, FileCollectionIncludeFlags
typealias FileCollectionsBrowseResponse = PageV2<FileCollection>

typealias FileCollectionsCreateRequest = BulkRequest<FileCollection.Spec>
typealias FileCollectionsCreateResponse = BulkResponse<FindByStringId>

typealias FileCollectionsDeleteRequest = BulkRequest<FindByStringId>
typealias FileCollectionsDeleteResponse = Unit

typealias FileCollectionsRenameRequest = BulkRequest<FileCollectionsRenameRequestItem>

@Serializable
data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsRenameResponse = Unit

typealias FileCollectionsUpdateAclRequest = BulkRequest<FileCollectionsUpdateAclRequestItem>

@Serializable
data class FileCollectionsUpdateAclRequestItem(
    val id: String,
    val newAcl: List<ResourceAclEntry<FilePermission>>,
)
typealias FileCollectionsUpdateAclResponse = Unit

@Serializable
data class FileCollectionsRetrieveRequest(
    val id: String,
    val provider: String,
    override val includeSupport: Boolean? = null,
) : FileCollectionIncludeFlags
typealias FileCollectionsRetrieveResponse = FileCollection

// ---

object FileCollections : CallDescriptionContainer("files.collections") {
    const val baseContext = "/api/files/collections"

    val browse = call<FileCollectionsBrowseRequest, FileCollectionsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val create = call<FileCollectionsCreateRequest, FileCollectionsCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val delete = call<FileCollectionsDeleteRequest, FileCollectionsDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val rename = call<FileCollectionsRenameRequest, FileCollectionsRenameResponse, CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename")
    }

    val updateAcl = call<FileCollectionsUpdateAclRequest, FileCollectionsUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    val retrieve = call<FileCollectionsRetrieveRequest, FileCollectionsRetrieveResponse,
        CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    /*
    // TODO Interface tbd
    val search = call<FileCollectionsSearchRequest, FileCollectionsSearchResponse, CommonErrorMessage>("search") {
    }
     */

    // TODO Quota?
}
