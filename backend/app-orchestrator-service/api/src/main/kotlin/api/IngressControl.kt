package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.*

typealias IngressControlUpdateRequest = BulkRequest<IngressControlUpdateRequestItem>

data class IngressControlUpdateRequestItem(
    override val id: String,
    val state: IngressState? = null,
    val status: String? = null,
    val clearBindingToJob: Boolean? = null,
) : IngressId
typealias IngressControlUpdateResponse = Unit

typealias IngressControlRetrieveRequest = IngressRetrieveWithFlags
typealias IngressControlRetrieveResponse = Ingress

typealias IngressControlChargeCreditsRequest = BulkRequest<IngressControlChargeCreditsRequestItem>

data class IngressControlChargeCreditsRequestItem(
    @UCloudApiDoc("The ID of the `Ingress`")
    val id: String,

    @UCloudApiDoc(
        "The ID of the charge\n\n" +
            "This charge ID must be unique for the `Ingress`, UCloud will reject charges which are not unique."
    )
    val chargeId: String,

    @UCloudApiDoc("Amount of units to charge the user")
    val units: Long,
)

data class IngressControlChargeCreditsResponse(
    @UCloudApiDoc(
        "A list of jobs which could not be charged due to lack of funds. " +
            "If all jobs were charged successfully then this will empty."
    )
    val insufficientFunds: List<IngressId>,

    @UCloudApiDoc(
        "A list of ingresses which could not be charged due to it being a duplicate charge. " +
            "If all ingresses were charged successfully this will be empty."
    )
    val duplicateCharges: List<IngressId>,
)

object IngressControl : CallDescriptionContainer("ingresses.control") {
    const val baseContext = "/api/ingresses/control"

    val update = call<IngressControlUpdateRequest, IngressControlUpdateResponse, CommonErrorMessage>("update") {
        httpUpdate(baseContext, "update", roles = Roles.PROVIDER)
    }

    val retrieve = call<IngressControlRetrieveRequest, IngressControlRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)
    }

    val chargeCredits = call<IngressControlChargeCreditsRequest,
        IngressControlChargeCreditsResponse, CommonErrorMessage>("chargeCredits") {
        httpUpdate(baseContext, "chargeCredits", roles = Roles.PROVIDER)
    }
}
