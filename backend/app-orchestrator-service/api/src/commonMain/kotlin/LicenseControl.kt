package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

typealias LicenseControlUpdateRequest = BulkRequest<LicenseControlUpdateRequestItem>

@Serializable
data class LicenseControlUpdateRequestItem(
    override val id: String,
    val state: LicenseState? = null,
    val status: String? = null,
) : LicenseId
typealias LicenseControlUpdateResponse = Unit

typealias LicenseControlRetrieveRequest = LicenseRetrieveWithFlags
typealias LicenseControlRetrieveResponse = License

typealias LicenseControlChargeCreditsRequest = BulkRequest<LicenseControlChargeCreditsRequestItem>

@Serializable
data class LicenseControlChargeCreditsRequestItem(
    @UCloudApiDoc("The ID of the `License`")
    val id: String,

    @UCloudApiDoc(
        "The ID of the charge\n\n" +
            "This charge ID must be unique for the `License`, UCloud will reject charges which are not unique."
    )
    val chargeId: String,

    @UCloudApiDoc("Amount of units to charge the user")
    val units: Long,
)

@Serializable
data class LicenseControlChargeCreditsResponse(
    @UCloudApiDoc(
        "A list of jobs which could not be charged due to lack of funds. " +
            "If all jobs were charged successfully then this will empty."
    )
    val insufficientFunds: List<LicenseId>,

    @UCloudApiDoc(
        "A list of ingresses which could not be charged due to it being a duplicate charge. " +
            "If all ingresses were charged successfully this will be empty."
    )
    val duplicateCharges: List<LicenseId>,
)

@TSNamespace("compute.licenses.control")
object LicenseControl : CallDescriptionContainer("licenses.control") {
    const val baseContext = "/api/licenses/control"

    val update = call<LicenseControlUpdateRequest, LicenseControlUpdateResponse, CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)
    }

    val retrieve = call<LicenseControlRetrieveRequest, LicenseControlRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)
    }

    val chargeCredits = call<LicenseControlChargeCreditsRequest,
        LicenseControlChargeCreditsResponse, CommonErrorMessage>("chargeCredits") {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)
    }
}
