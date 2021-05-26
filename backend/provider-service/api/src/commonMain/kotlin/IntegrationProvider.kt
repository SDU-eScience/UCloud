package dk.sdu.cloud.provider.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.withHooks
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

typealias IntegrationProviderRetrieveManifestRequest = Unit

@Serializable
data class IntegrationProviderRetrieveManifestResponse(
    val enabled: Boolean,
)

@Serializable
data class IntegrationProviderInitRequest(val username: String)

@Serializable
data class IntegrationProviderConnectRequest(val username: String)

@Serializable
data class IntegrationProviderConnectResponse(val redirectTo: String)

@Serializable
data class IntegrationProviderWelcomeRequest(val token: String, val createdProvider: Provider)
typealias IntegrationProviderWelcomeResponse = Unit

@UCloudApiDoc("Provider interface for the integration module")
open class IntegrationProvider(namespace: String) : CallDescriptionContainer("$namespace.im") {
    private val baseContext = "/ucloud/$namespace/integration"

    val retrieveManifest = call<IntegrationProviderRetrieveManifestRequest,
        IntegrationProviderRetrieveManifestResponse, CommonErrorMessage>("retrieveManifest") {
        httpRetrieve(baseContext, "manifest", roles = Roles.PRIVILEGED)
    }

    val init = call<IntegrationProviderInitRequest, Unit, CommonErrorMessage>("init") {
        httpUpdate(baseContext, "init", roles = Roles.PRIVILEGED)
    }

    val connect = call<IntegrationProviderConnectRequest, IntegrationProviderConnectResponse,
        CommonErrorMessage>("connect") {
        httpUpdate(baseContext, "connect", roles = Roles.PRIVILEGED)
    }

    val welcome = call<IntegrationProviderWelcomeRequest,
        IntegrationProviderWelcomeResponse, CommonErrorMessage>("welcome") {
        httpUpdate(baseContext, "welcome", roles = Roles.PUBLIC)
    }

    companion object {
        const val UCLOUD_USERNAME_HEADER = "UCloud-Username"
    }
}

fun AuthenticatedClient.withProxyInfo(username: String?): AuthenticatedClient {
    return withHooks(
        beforeHook = {
            if (username != null) {
                when (it) {
                    is OutgoingHttpCall -> {
                        it.builder.header(IntegrationProvider.UCLOUD_USERNAME_HEADER, username)
                    }

                    is OutgoingWSCall -> {
                        it.attributes[OutgoingWSCall.proxyAttribute] = username
                    }

                    else -> {
                        throw IllegalArgumentException("Cannot attach proxy info to this client $it")
                    }
                }
            }
        }
    )
}