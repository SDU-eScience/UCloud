package dk.sdu.cloud.provider.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class IntegrationConnectRequest(val provider: String)

@Serializable
data class IntegrationConnectResponse(val redirectTo: String)

@Serializable
data class IntegrationBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias IntegrationBrowseResponse = PageV2<IntegrationBrowseResponseItem>

@Serializable
data class IntegrationBrowseResponseItem(val provider: String, val connected: Boolean)

@Serializable
data class IntegrationClearConnectionRequest(val username: String, val provider: String)
typealias IntegrationClearConnectionResponse = Unit

object Integration : CallDescriptionContainer("providers.im") {
    const val baseContext = "/api/providers/integration"

    val connect = call<IntegrationConnectRequest, IntegrationConnectResponse, CommonErrorMessage>("connect") {
        httpUpdate(baseContext, "connect")
    }

    val browse = call<IntegrationBrowseRequest, IntegrationBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val clearConnection = call<IntegrationClearConnectionRequest, IntegrationClearConnectionResponse,
        CommonErrorMessage>("clearConnection") {
        httpUpdate(baseContext, "clearConnection", roles = Roles.PRIVILEGED)
    }
}
