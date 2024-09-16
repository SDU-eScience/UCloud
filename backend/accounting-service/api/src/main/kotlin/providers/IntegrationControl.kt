package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class IntegrationControlApproveConnectionRequest(val username: String)
typealias IntegrationControlApproveConnectionResponse = Unit

object IntegrationControl : CallDescriptionContainer("providers.im.control") {
    const val baseContext = "/api/providers/integration/control"

    val approveConnection = call("approveConnection", IntegrationControlApproveConnectionRequest.serializer(), IntegrationControlApproveConnectionResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "approveConnection", Roles.PROVIDER)
    }

    val clearConnection = call("clearConnection", IntegrationClearConnectionRequest.serializer(), IntegrationClearConnectionResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "clearConnection", roles = Roles.PROVIDER)
    }

    val reverseConnection = ReverseConnection.call

    object ReverseConnection {
        @Serializable
        class Request()

        @Serializable
        data class Response(
            val token: String,
        )

        val call = call(
            "reverseConnection",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "reverseConnection", roles = Roles.PROVIDER)
            }
        )
    }
}
