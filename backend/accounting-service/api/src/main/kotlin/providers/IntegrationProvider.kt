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
@Deprecated("Use the simpler register endpoint instead")
data class ProviderWelcomeTokens(val refreshToken: String, val publicKey: String)

@Serializable
data class IntegrationProviderUnlinkedRequest(val username: String)

@Serializable
@Deprecated("Use the simpler register endpoint instead")
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

    @Deprecated("Use the simpler register endpoint instead")
    val welcome = call("welcome", IntegrationProviderWelcomeRequest.serializer(), IntegrationProviderWelcomeResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "welcome", roles = Roles.PUBLIC)
    }

    val unlinked = call("unlinked", IntegrationProviderUnlinkedRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "unlinked", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Callback when a provider unlinks their connection with the provider"
            description = """
                This call is made in the context of the user. As a result, the message is signed by the end-user. This 
                callback will be made whenever a user manually unlinks their account with a provider. This callback
                will only be made if UCloud also believes there has been an unlinking attempt.
                
                This endpoint is optional and if UCloud/Core receives any non 5xx status code then the unlinking is
                accepted by UCloud/Core. If the provider responds with 5xx or does not respond at all then the unlinking
                process will fail and the end-user is notified.
            """.trimIndent()
        }
    }

    companion object {
        const val UCLOUD_USERNAME_HEADER = "UCloud-Username"
        const val UCLOUD_SIGNED_INTENT = "UCloud-Signed-Intent"
    }
}
