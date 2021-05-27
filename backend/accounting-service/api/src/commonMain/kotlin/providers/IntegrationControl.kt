package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class IntegrationControlApproveConnectionRequest(val username: String)
typealias IntegrationControlApproveConnectionResponse = Unit

object IntegrationControl : CallDescriptionContainer("providers.im.control") {
    const val baseContext = "/api/providers/integration/control"

    val approveConnection = call<IntegrationControlApproveConnectionRequest,
        IntegrationControlApproveConnectionResponse, CommonErrorMessage>("approveConnection") {
        httpUpdate(baseContext, "approveConnection", Roles.PROVIDER)
    }
}