package dk.sdu.cloud.auth.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthProvidersRegisterRequestItem(val id: String)

@Serializable
data class AuthProvidersRegisterResponseItem(val claimToken: String)

@Serializable
data class PublicKeyAndRefreshToken(
    val providerId: String,
    val publicKey: String,
    val refreshToken: String,
)

typealias AuthProvidersRenewRequestItem = FindByStringId

typealias AuthProvidersRefreshRequest = BulkRequest<AuthProvidersRefreshRequestItem>
typealias AuthProvidersRefreshRequestItem = RefreshToken
typealias AuthProvidersRefreshResponse = BulkResponse<AccessToken>
typealias AuthProvidersRefreshAudit = FindByStringId

typealias AuthProvidersRetrievePublicKeyRequest = FindByStringId

@Serializable
data class AuthProvidersRetrievePublicKeyResponse(val publicKey: String)

typealias AuthProvidersRefreshAsProviderRequest = BulkRequest<AuthProvidersRefreshAsProviderRequestItem>

@Serializable
data class AuthProvidersRefreshAsProviderRequestItem(val providerId: String)
typealias AuthProvidersRefreshAsProviderResponse = BulkResponse<AccessToken>

typealias AuthProvidersGenerateKeyPairRequest = Unit
@Serializable
data class AuthProvidersGenerateKeyPairResponse(
    val publicKey: String,
    val privateKey: String,
)

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object AuthProviders : CallDescriptionContainer("auth.providers") {
    const val baseContext = "/auth/providers"
    const val PROVIDER_PREFIX = "#P_"

    @UCloudApiInternal(InternalLevel.BETA)
    val register = call<BulkRequest<AuthProvidersRegisterRequestItem>, BulkResponse<AuthProvidersRegisterResponseItem>,
        CommonErrorMessage>("register") {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val claim = call<BulkRequest<AuthProvidersRegisterResponseItem>, BulkResponse<PublicKeyAndRefreshToken>,
        CommonErrorMessage>("claim") {
        httpUpdate(baseContext, "claim", roles = Roles.PRIVILEGED)
    }

    val renew = call<BulkRequest<AuthProvidersRenewRequestItem>, BulkResponse<PublicKeyAndRefreshToken>,
        CommonErrorMessage>("renew") {
        httpUpdate(baseContext, "renew", roles = Roles.PRIVILEGED)
    }

    val refresh = call<AuthProvidersRefreshRequest, AuthProvidersRefreshResponse, CommonErrorMessage>("refresh") {
        audit<BulkResponse<AuthProvidersRefreshAudit>>()
        httpUpdate(baseContext, "refresh", roles = Roles.PUBLIC)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val retrievePublicKey = call<AuthProvidersRetrievePublicKeyRequest, AuthProvidersRetrievePublicKeyResponse,
        CommonErrorMessage>("retrievePublicKey") {
        httpRetrieve(baseContext, "key", roles = Roles.PRIVILEGED)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val refreshAsOrchestrator = call<AuthProvidersRefreshAsProviderRequest, AuthProvidersRefreshAsProviderResponse,
        CommonErrorMessage>("refreshAsOrchestrator") {
        httpUpdate(baseContext, "refreshAsOrchestrator", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Signs an access-token to be used by a UCloud service"
            description = """
                This RPC signs an access-token which will be used by authorized UCloud services to act as an
                orchestrator of resources.
            """.trimIndent()
        }
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val generateKeyPair = call<AuthProvidersGenerateKeyPairRequest, AuthProvidersGenerateKeyPairResponse,
        CommonErrorMessage>("generateKeyPair") {
        httpUpdate(baseContext, "generateKeyPair", roles = Roles.PRIVILEGED)

        documentation {
            summary = "Generates an RSA key pair useful for JWT signatures"
            description = """
                Generates an RSA key pair and returns it to the client. The key pair is not stored or registered in any
                way by the authentication service.
            """.trimIndent()
        }
    }
}
