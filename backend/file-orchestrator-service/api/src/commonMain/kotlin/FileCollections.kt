package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

// ---

typealias FileCollectionsRenameRequest = BulkRequest<FileCollectionsRenameRequestItem>

@Serializable
data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsRenameResponse = Unit

// ---

object FileCollections/* : ResourceApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    override val typeInfo = ResourceTypeInfo<FileCollection, FileCollection.Spec, FileCollection.Update,
        FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>()

    override val delete: CallDescription<BulkRequest<FindByStringId>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!

    val rename = call<FileCollectionsRenameRequest, FileCollectionsRenameResponse, CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename")
    }

    /*
    const val baseContext = "/api/files/collections"

    TODO Need includeSupport to be compatible
    val browse = call<FileCollectionsBrowseRequest, FileCollectionsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    This is identical
    val create = call<FileCollectionsCreateRequest, FileCollectionsCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    The provider has been removed, otherwise identical
    val delete = call<FileCollectionsDeleteRequest, FileCollectionsDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    New API
    val updateAcl = call<FileCollectionsUpdateAclRequest, FileCollectionsUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl")
    }

    Provider has been removed
    val retrieve = call<FileCollectionsRetrieveRequest, FileCollectionsRetrieveResponse,
        CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    New API
    val retrieveManifest = call<FileCollectionsRetrieveManifestRequest, FileCollectionsRetrieveManifestResponse,
        CommonErrorMessage>("retrieveManifest") {
        httpRetrieve(baseContext, "manifest")
    }

     */

    /*
    // TODO Interface tbd
    val search = call<FileCollectionsSearchRequest, FileCollectionsSearchResponse, CommonErrorMessage>("search") {
    }
     */
}

   */