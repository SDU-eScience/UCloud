package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.SpecificationAndPermissions
import kotlinx.serialization.Serializable

// ---

typealias FileCollectionsControlUpdateRequest = BulkRequest<FileCollectionsControlUpdateRequestItem>

@Serializable
data class FileCollectionsControlUpdateRequestItem(
    val id: String,
    val update: FileCollection.Update,
)
typealias FileCollectionsControlUpdateResponse = Unit

typealias FileCollectionsControlChargeCreditsRequest = BulkRequest<FileCollectionsControlChargeCreditsRequestItem>

@Serializable
data class FileCollectionsControlChargeCreditsRequestItem(
    val id: String,
    val chargeId: String,
    val units: Long,
)

@Serializable
data class FileCollectionsControlChargeCreditsResponse(
    val insufficientFunds: List<FindByStringId>,
    val duplicateCharges: List<FindByStringId>,
)

typealias FileCollectionsControlCreateRequest = BulkRequest<SpecificationAndPermissions<FileCollection.Spec>>
typealias FileCollectionsControlCreateResponse = BulkResponse<FindByStringId>

// ---

object FileCollectionsControl : CallDescriptionContainer("files.collections.control") {
    const val baseContext = "/api/files/collections/control"

    val update = call<FileCollectionsControlUpdateRequest, FileCollectionsControlUpdateResponse,
        CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)
    }

    val chargeCredits = call<FileCollectionsControlChargeCreditsRequest, FileCollectionsControlChargeCreditsResponse,
        CommonErrorMessage>("chargeCredits") {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)
    }

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
}
