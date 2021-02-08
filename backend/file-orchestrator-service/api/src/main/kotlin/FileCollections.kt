package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.WithPaginationRequestV2

// ---

data class FileCollectionsBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias FileCollectionsBrowseResponse = PageV2<FileCollection>

typealias FileCollectionsCreateRequest = BulkRequest<FileCollection.Spec>
typealias FileCollectionsCreateResponse = BulkResponse<FindByStringId>

typealias FileCollectionsDeleteRequest = BulkRequest<FindByStringId>
typealias FileCollectionsDeleteResponse = Unit

typealias FileCollectionsRenameRequest = BulkRequest<FileCollectionsRenameRequestItem>

data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsRenameResponse = Unit

typealias FileCollectionsUpdateAclRequest = BulkRequest<FileCollectionsUpdateAclRequestItem>

data class FileCollectionsUpdateAclRequestItem(
    val id: String,
    val newAcl: List<ResourceAclEntry<FilePermission>>,
)
typealias FileCollectionsUpdateAclResponse = Unit

typealias FileCollectionsRetrieveRequest = FindByStringId
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
