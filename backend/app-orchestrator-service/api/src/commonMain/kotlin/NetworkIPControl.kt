package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

typealias NetworkIPControlUpdateRequest = BulkRequest<NetworkIPControlUpdateRequestItem>

@Serializable
data class NetworkIPControlUpdateRequestItem(
    override val id: String,
    val state: NetworkIPState? = null,
    val status: String? = null,
    val clearBindingToJob: Boolean? = null,
    val changeIpAddress: Boolean? = null,
    val newIpAddress: String? = null,
) : NetworkIPId
typealias NetworkIPControlUpdateResponse = Unit

typealias NetworkIPControlRetrieveRequest = NetworkIPRetrieveWithFlags
typealias NetworkIPControlRetrieveResponse = NetworkIP

typealias NetworkIPControlChargeCreditsRequest = BulkRequest<NetworkIPControlChargeCreditsRequestItem>

@Serializable
data class NetworkIPControlChargeCreditsRequestItem(
    @UCloudApiDoc("The ID of the `NetworkIP`")
    val id: String,

    @UCloudApiDoc(
        "The ID of the charge\n\n" +
            "This charge ID must be unique for the `NetworkIP`, UCloud will reject charges which are not unique."
    )
    val chargeId: String,

    @UCloudApiDoc("Amount of units to charge the user")
    val units: Long,
)

@Serializable
data class NetworkIPControlChargeCreditsResponse(
    @UCloudApiDoc(
        "A list of jobs which could not be charged due to lack of funds. " +
            "If all jobs were charged successfully then this will empty."
    )
    val insufficientFunds: List<NetworkIPId>,

    @UCloudApiDoc(
        "A list of ingresses which could not be charged due to it being a duplicate charge. " +
            "If all ingresses were charged successfully this will be empty."
    )
    val duplicateCharges: List<NetworkIPId>,
)

@TSNamespace("compute.networkips.control")
object NetworkIPControl : CallDescriptionContainer("networkips.control") {
    const val baseContext = "/api/networkips/control"

    val update = call<NetworkIPControlUpdateRequest, NetworkIPControlUpdateResponse, CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)
    }

    val retrieve = call<NetworkIPControlRetrieveRequest, NetworkIPControlRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)
    }

    val chargeCredits = call<NetworkIPControlChargeCreditsRequest,
        NetworkIPControlChargeCreditsResponse, CommonErrorMessage>("chargeCredits") {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)
    }
}
