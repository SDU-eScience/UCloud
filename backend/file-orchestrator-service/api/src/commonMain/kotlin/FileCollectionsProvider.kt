package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.ResourceAclEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// ---

@Serializable
data class ProxiedRequest<T> internal constructor(
    val username: String,
    val project: String?,
    val request: T,
) {
    companion object {
        fun <T> createIPromiseToHaveVerifiedTheProjectBeforeCreating(
            username: String,
            project: String?,
            request: T,
        ): ProxiedRequest<T> {
            return ProxiedRequest(username, project, request)
        }
    }
}

typealias FileCollectionsProviderRenameRequest = ProxiedRequest<BulkRequest<FileCollectionsProviderRenameRequestItem>>

@Serializable
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
)
typealias FileCollectionsProviderRenameResponse = Unit


// ---

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

    /*
    val baseContext = "/ucloud/$namespace/files/collections"

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



    val updateAcl = call<FileCollectionsProviderUpdateAclRequest, FileCollectionsProviderUpdateAclResponse,
        CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "updateAcl", roles = Roles.SERVICE)
    }
     */
}
