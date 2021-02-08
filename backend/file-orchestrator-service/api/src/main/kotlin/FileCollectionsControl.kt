package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*

// ---

typealias FileCollectionsControlUpdateRequest = BulkRequest<FileCollectionsControlUpdateRequestItem>

data class FileCollectionsControlUpdateRequestItem(
    val id: String,
    val update: FileCollection.Update,
)
typealias FileCollectionsControlUpdateResponse = Unit

typealias FileCollectionsControlChargeCreditsRequest = BulkRequest<FileCollectionsControlChargeCreditsRequestItem>

data class FileCollectionsControlChargeCreditsRequestItem(
    val id: String,
    val chargeId: String,
    val units: Long,
)

data class FileCollectionsControlChargeCreditsResponse(
    val insufficientFunds: List<FindByStringId>,
    val duplicateCharges: List<FindByStringId>,
)

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
}
