package dk.sdu.cloud.provider.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class IntegrationConnectRequest(val provider: String)

@Serializable
data class IntegrationConnectResponse(val redirectTo: String) {
    override fun toString(): String {
        return "IntegrationConnectResponse()"
    }
}

@Serializable
data class IntegrationBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias IntegrationBrowseResponse = PageV2<IntegrationBrowseResponseItem>

@Serializable
data class IntegrationBrowseResponseItem(
    val provider: String,
    val connected: Boolean,
    val providerTitle: String,
    val requiresMessageSigning: Boolean,
)

@Serializable
data class IntegrationClearConnectionRequest(
    val username: String? = null,
    val provider: String? = null,
)
typealias IntegrationClearConnectionResponse = Unit

object Integration : CallDescriptionContainer("providers.im") {
    const val baseContext = "/api/providers/integration"

    val connect = call("connect", IntegrationConnectRequest.serializer(), IntegrationConnectResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "connect")
    }

    val browse = call("browse", IntegrationBrowseRequest.serializer(), PageV2.serializer(IntegrationBrowseResponseItem.serializer()), CommonErrorMessage.serializer()) {
        httpBrowse(baseContext)
    }

    val clearConnection = call("clearConnection", IntegrationClearConnectionRequest.serializer(), IntegrationClearConnectionResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "clearConnection", roles = Roles.AUTHENTICATED)
    }
}
