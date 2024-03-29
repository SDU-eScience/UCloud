package dk.sdu.cloud.auth.api

import dk.sdu.cloud.Actor
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.safeUsername
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class AuthProvidersRegisterRequestItem(val id: String)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class AuthProvidersRegisterResponseItem(val claimToken: String)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
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
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class AuthProvidersRetrievePublicKeyResponse(val publicKey: String)

typealias AuthProvidersRefreshAsProviderRequest = BulkRequest<AuthProvidersRefreshAsProviderRequestItem>

@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class AuthProvidersRefreshAsProviderRequestItem(val providerId: String)
typealias AuthProvidersRefreshAsProviderResponse = BulkResponse<AccessToken>

typealias AuthProvidersGenerateKeyPairRequest = Unit
@Serializable
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class AuthProvidersGenerateKeyPairResponse(
    val publicKey: String,
    val privateKey: String,
)

val Actor.providerIdOrNull: String?
    get() {
        val uname = safeUsername()
        return if (uname.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            uname.substring(AuthProviders.PROVIDER_PREFIX.length)
        } else {
            null
        }
    }

@UCloudApiExperimental(ExperimentalLevel.BETA)
object AuthProviders : CallDescriptionContainer("auth.providers") {
    const val baseContext = "/auth/providers"
    const val PROVIDER_PREFIX = "#P_"

    init {
        description = """
UCloud/Core and Providers authenticate each-other using short-lived JWTs and long-lived opaque refresh-tokens.
            
## Communication

All communication between UCloud and a provider is done via an HTTP(S) API, certain optional endpoints use a WebSocket
API. Note that the WebSocket protocol is an extension of HTTP, as a result the WebSocket API shares the same security
aspects as the HTTP API.

TLS is strictly required for all providers running in a production environment. The provider must use a valid
certificate, which hasn't expired and signed by a commonly recognized Certificate Authority (CA). TLS for HTTPS
connections are handled internally in UCloud by OpenJDK11+. Notably, this means that TLSv1.3 is supported. We encourage
providers to follow best practices. For inspiration, Mozilla hosts an
online [SSL configuration generator](https://ssl-config.mozilla.org).
Additionally, [this document](https://github.com/ssllabs/research/wiki/SSL-and-TLS-Deployment-Best-Practices) from SSL
Labs can provide a good starting point.

Providers should treat UCloud similarly. An integration module should ensure that all certificates served by UCloud are
valid and signed by a commonly recognized CA.

For __local development purposes only__ UCloud can communicate with a __local__ provider using HTTP. It is not possible
to configure UCloud to use self-signed certificates, and as a result it is not possible to run a local provider with a
self-signed certificate + TLS. This design choice has been made to simplify the code and avoid poorly configured UCloud
deployments.

## Authentication and Authorization

UCloud _and_ the provider authenticates and authorizes all ingoing requests. Short-lived
[JSON Web Tokens (JWT)](https://jwt.io) protect these requests.

| Token | Type | Description |
|-------|------|-------------|
| `accessToken` | [JWT](https://jwt.io) | A short-lived JWT token for authenticating regular requests |
| `refreshToken` | Opaque | An opaque token, with no explicit expiration, used for requesting new `accessToken`s |

__Table:__ The two token types used in UCloud ↔ Provider authentication

Because JWTs are short-lived, every provider must renew their JWT periodically. Providers do this by using an opaque
token called the `refreshToken`. The diagram below shows how a provider can use their `refreshToken` to generate a new
`accessToken`.

<!--
# https://sequencediagram.org/
title Requesting an access-token

database Auth DB
participant UCloud
participant "Provider P" as p


p->UCloud: auth.refresh() authenticated with refreshToken
UCloud->Auth DB: validate(refreshToken)
Auth DB->UCloud: SecurityPrincipal representing P
UCloud->p: AccessToken representing P

==P can now use the access-token==

p->UCloud: jobs.control.update(...) authenticated with accessToken
UCloud->Auth DB: Fetch keypair for P
Auth DB->UCloud: Keypair
note over UCloud: Verify accessToken using keypair
UCloud->p: OK
-->

![](/backend/app-orchestrator-service/wiki/access_token_request.svg)

__Figure:__ A provider requesting a new `accessToken` using their `refreshToken`

All calls use
the [HTTP bearer authentication scheme](https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes)
. As a result the `refreshToken` will be passed in the `Authorization` header like this:

```
HTTP/1.1 POST https://cloud.sdu.dk/auth/refresh
Authorization: Bearer ${"$"}refreshToken
```

The `accessToken` is passed similarly:

```
HTTP/1.1 POST https://cloud.sdu.dk/some/call
Authorization: Bearer ${"$"}accessToken
```

### Internals of `accessToken`s

In this section we describe the internals of the `accessToken` and how to verify an `accessToken` from UCloud. It is
important that all providers authenticate _every_ request they receive.

The payload of an `accessToken` is follows the same schema for both UCloud and providers.

```
{
  // Token properties
  "iat": 1234,
  "exp": 5678,
  "iss": "cloud.sdu.dk",
   
  // Authorization properties
  "role": "<ROLE>", // "SERVICE" if the token authenticates UCloud. "PROVIDER" if the token authenticates a provider.

  // User metadata
  "sub": "<USERNAME>" // A unique identifier for the provider or "_UCloud" if the token authenticates UCloud.
}
```

All JWTs signed by UCloud will use the `RS256` algorithm, internally this uses `RSASSA-PKCS1-v1_5` with `SHA-256` used
for the signature. UCloud uses a unique private & public keypair for every provider. The provider receives UCloud's
public key when the `refreshToken` of the provider is issued. UCloud will generate a new keypair if the provider's
`refreshToken` is revoked.

### Verifying `accessToken`s

As a provider, you must take the following steps to verify the authenticity of an `accessToken`:

1. Verify that the `accessToken` is signed with the `RS256` algorithm (`alg` field of the JWT header)
2. Verify that the `sub` field is equal to `"_UCloud"` (Note the '\_' prefix)
3. Verify that the `iat` (issued at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.6](https://tools.ietf.org/html/rfc7519#section-4.1.6))
3. Verify that the `exp` (expires at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.4](https://tools.ietf.org/html/rfc7519#section-4.1.4))
4. Verify that the `iss` (issuer) field is equal to `"cloud.sdu.dk"`
5. Verify that the `role` field is equal to `SERVICE`

It is absolutely critical that JWT verification is configured correctly. For example, some JWT verifiers are known for
having too relaxed defaults, which in the worst case will skip all verification. It is important that the verifier is
configured to _only_ accept the parameters mentioned above.

        """.trimIndent()
    }

    private const val authenticationUseCase = "authentication"
    override fun documentation() {
        useCase(
            authenticationUseCase,
            "A Provider authenticating with UCloud/Core",
            preConditions = listOf(
                "The provider has already been registered with UCloud/Core",
            ),
            flow = {
                val ucloud = ucloudCore()
                val provider = provider()

                comment("📝 Note: The tokens shown here are not representative of tokens you will see in practice")

                success(
                    AuthProviders.refresh,
                    bulkRequestOf(
                        AuthProvidersRefreshRequestItem("fb69e4367ee0fe4c76a4a926394aee547a41d998")
                    ),
                    BulkResponse(
                        listOf(
                            AccessToken(
                                "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9." +
                                    "eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9." +
                                    "P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
                            )
                        )
                    ),
                    provider
                )
            }
        )
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val register = call("register", BulkRequest.serializer(AuthProvidersRegisterRequestItem.serializer()), BulkResponse.serializer(AuthProvidersRegisterResponseItem.serializer()), CommonErrorMessage.serializer()) {
        httpCreate(baseContext, roles = Roles.PRIVILEGED)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val claim = call("claim", BulkRequest.serializer(AuthProvidersRegisterResponseItem.serializer()), BulkResponse.serializer(PublicKeyAndRefreshToken.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "claim", roles = Roles.PRIVILEGED)
    }

    val renew = call("renew", BulkRequest.serializer(AuthProvidersRenewRequestItem.serializer()), BulkResponse.serializer(PublicKeyAndRefreshToken.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "renew", roles = Roles.PRIVILEGED)
    }

    @UCloudApiStable
    val refresh = call("refresh", AuthProvidersRefreshRequest.serializer(AuthProvidersRefreshRequestItem.serializer()), AuthProvidersRefreshResponse.serializer(AccessToken.serializer()), CommonErrorMessage.serializer()) {
        audit(BulkResponse.serializer(AuthProvidersRefreshAudit.serializer()))
        httpUpdate(baseContext, "refresh", roles = Roles.PUBLIC)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val retrievePublicKey = call("retrievePublicKey", AuthProvidersRetrievePublicKeyRequest.serializer(), AuthProvidersRetrievePublicKeyResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "key", roles = Roles.PRIVILEGED)
    }

    @UCloudApiInternal(InternalLevel.BETA)
    val refreshAsOrchestrator = call("refreshAsOrchestrator", BulkRequest.serializer(AuthProvidersRefreshAsProviderRequestItem.serializer()), BulkResponse.serializer(AccessToken.serializer()), CommonErrorMessage.serializer()) {
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
    val generateKeyPair = call("generateKeyPair", AuthProvidersGenerateKeyPairRequest.serializer(), AuthProvidersGenerateKeyPairResponse.serializer(), CommonErrorMessage.serializer()) {
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
