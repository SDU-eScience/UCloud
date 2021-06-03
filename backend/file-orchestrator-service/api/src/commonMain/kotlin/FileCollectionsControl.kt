package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo

object FileCollectionsControl : ResourceControlApi<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>("files.collections") {

    override val typeInfo = ResourceTypeInfo<FileCollection, FileCollection.Spec, FileCollection.Update,
        FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport>()

    /*
    val update = call<FileCollectionsControlUpdateRequest, FileCollectionsControlUpdateResponse,
        CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)
    }

    val chargeCredits = call<FileCollectionsControlChargeCreditsRequest, FileCollectionsControlChargeCreditsResponse,
        CommonErrorMessage>("chargeCredits") {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)
    }

    Name is changing and TODO permissions
    val create = call<FileCollectionsControlCreateRequest, FileCollectionsControlCreateResponse,
        CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = Roles.PROVIDER)

        documentation {
            summary = "Register a file-collection created out-of-band"
            description = """
                This endpoint can be used to register a file-collection which has been created out-of-band by the
                end-user or a system administrator. This will register the collection in UCloud's internal catalogue.
                Provider's must specify the owner of the resource and can optionally specify additional permissions
                that have been applied to the collection.
            """.trimIndent()
        }
    }
     */
}
