package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

typealias IntegrationProviderRetrieveManifestRequest = Unit

@Serializable
data class IntegrationProviderRetrieveManifestResponse(
    val enabled: Boolean,
    val expireAfterMs: Long? = null,
    val requiresMessageSigning: Boolean = false,
)

@Serializable
data class IntegrationProviderInitRequest(val username: String)

@Serializable
data class IntegrationProviderConnectRequest(val username: String)

@Serializable
data class IntegrationProviderConnectResponse(val redirectTo: String)

@Serializable
data class ProviderWelcomeTokens(val refreshToken: String, val publicKey: String)

@Serializable
data class IntegrationProviderWelcomeRequest(val token: String, val createdProvider: ProviderWelcomeTokens)
typealias IntegrationProviderWelcomeResponse = Unit

@UCloudApiDoc("Provider interface for the integration module")
open class IntegrationProvider(namespace: String) : CallDescriptionContainer("$namespace.im") {
    private val baseContext = "/ucloud/$namespace/integration"

    val retrieveManifest = call("retrieveManifest", IntegrationProviderRetrieveManifestRequest.serializer(), IntegrationProviderRetrieveManifestResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "manifest", roles = Roles.PRIVILEGED)
    }

    val init = call("init", IntegrationProviderInitRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "init", roles = Roles.PRIVILEGED)
    }

    val connect = call("connect", IntegrationProviderConnectRequest.serializer(), IntegrationProviderConnectResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "connect", roles = Roles.PRIVILEGED)
    }

    val welcome = call("welcome", IntegrationProviderWelcomeRequest.serializer(), IntegrationProviderWelcomeResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "welcome", roles = Roles.PUBLIC)
    }

    companion object {
        const val UCLOUD_USERNAME_HEADER = "UCloud-Username"
        const val UCLOUD_SIGNED_INTENT = "UCloud-Signed-Intent"
    }
}
