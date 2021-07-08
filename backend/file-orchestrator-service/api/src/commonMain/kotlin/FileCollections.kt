package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

typealias FileCollectionsRenameRequest = BulkRequest<FileCollectionsRenameRequestItem>

@Serializable
data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsRenameResponse = Unit

typealias FileCollectionsProviderRenameRequest = BulkRequest<FileCollectionsProviderRenameRequestItem>

@Serializable
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsProviderRenameResponse = Unit

// ---

object FileCollections : ResourceApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    override val typeInfo = ResourceTypeInfo<FileCollection, FileCollection.Spec, FileCollection.Update,
        FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>()

    override val delete: CallDescription<BulkRequest<FindByStringId>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!

    val rename = call<FileCollectionsRenameRequest, FileCollectionsRenameResponse, CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename")
    }
}

object FileCollectionsControl : ResourceControlApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    override val typeInfo = ResourceTypeInfo<FileCollection, FileCollection.Spec, FileCollection.Update,
        FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>()
}

open class FileCollectionsProvider(
    provider: String,
) : ResourceProviderApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections", provider) {

    override val typeInfo = ResourceTypeInfo<FileCollection, FileCollection.Spec, FileCollection.Update,
        FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>()

    val rename = call<FileCollectionsProviderRenameRequest, FileCollectionsProviderRenameResponse,
        CommonErrorMessage>("rename") {
        httpUpdate(baseContext, "rename", roles = Roles.SERVICE)
    }

    override val delete: CallDescription<BulkRequest<FileCollection>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!
}
