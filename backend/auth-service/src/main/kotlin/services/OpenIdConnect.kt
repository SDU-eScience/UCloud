package dk.sdu.cloud.auth.services

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpMethod
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.microWhichIsConfiguringCalls
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.security.SecureRandom
import java.util.*
import kotlin.collections.HashMap

@Serializable
@SerialName("OpenIdConnect")
data class OpenIdConnectIdpConfig(
    val endpoints: Endpoints,
    val client: Client,
    val signing: Signing,
    val experimental: Experimental = Experimental(),
) : IdentityProviderConfiguration() {
    @Serializable
    data class Endpoints(
        val auth: String,
        val token: String,
    )

    @Serializable
    data class Client(
        val id: String,
        val secret: String
    )

    @Serializable
    data class Signing(
        val algorithm: SignatureType,
        val key: String,
        val issuer: String? = null,
    )

    enum class SignatureType {
        // NOTE(Dan): This is incomplete
        RS256,
        ES256
    }

    @Serializable
    data class Experimental(
        val tokenToUse: TokenToUse = TokenToUse.id_token
    )

    enum class TokenToUse {
        // NOTE(Dan): The spec says you should use this
        id_token,

        // NOTE(Dan): ...and not this one.
        access_token
    }

    override fun toString(): String = "OpenIdConnectIdpConfig"
}

class OpenIdConnect(
    private val micro: Micro,
    private val rpcClient: RpcClient,
    private val ownHost: HostInfo,
    private val debugSystem: DebugSystem,
) : ConfiguredIdentityProvider {
    private lateinit var tokenEndpoint: String
    private lateinit var authEndpoint: String
    override lateinit var metadata: IdentityProviderMetadata
    private lateinit var configuration: OpenIdConnectIdpConfig
    private lateinit var loginCompleteFn: LoginCompleteFn

    private val log = Logger("OpenIdConnect")

    override suspend fun configure(metadata: IdentityProviderMetadata, loginCompleteFn: LoginCompleteFn) {
        this.metadata = metadata
        this.configuration = metadata.configuration as OpenIdConnectIdpConfig
        this.tokenEndpoint = this.configuration.endpoints.token.removeSuffix("?").removeSuffix("/")
        this.authEndpoint = this.configuration.endpoints.auth.removeSuffix("?").removeSuffix("/")
        this.loginCompleteFn = loginCompleteFn
    }

    private fun OpenIdConnectIdpConfig.hostInfo(): HostInfo {
        val tokenEndpoint = endpoints.token

        val schema = when {
            tokenEndpoint.startsWith("http://") -> "http"
            tokenEndpoint.startsWith("https://") -> "https"
            else -> error("Invalid tokenEndpoint supplied ('$tokenEndpoint')")
        }

        val endpointWithoutSchema = tokenEndpoint.removePrefix("$schema://")
        val hostWithoutSchema = endpointWithoutSchema.substringBefore("/")
        val host = hostWithoutSchema.substringBefore(":")
        val portString = hostWithoutSchema.substringAfter(":", missingDelimiterValue = "")
        val port = if (portString == "") null else portString.toIntOrNull()

        return HostInfo(host, schema, port)
    }

    // The state table is used to map a connection attempt to an active OIDC authentication flow.
    private class ConnectionState()
    @JvmInline
    private value class OidcState(val state: String)

    private val stateTableMutex = Mutex()
    private val stateTable = HashMap<OidcState, ConnectionState>()

    override suspend fun configureRpcServer(server: RpcServer) {
        // Token validator used to verify tokens returned by the OpenID provider (explained below).
        val accessTokenValidator: TokenValidationJWT = run {
            val signing = configuration.signing
            when (signing.algorithm) {
                OpenIdConnectIdpConfig.SignatureType.RS256 -> {
                    InternalTokenValidationJWT.withPublicCertificate(signing.key, issuer = signing.issuer)
                }

                OpenIdConnectIdpConfig.SignatureType.ES256 -> {
                    InternalTokenValidationJWT.withEs256(
                        signing.key,
                        issuer = signing.issuer,
                    )
                }
            }
        }

        // Implemented by us
        val openIdClientApi = object : CallDescriptionContainer("openidclient") {
            val callback = call(
                "callback",
                OpenIdConnectCallback.serializer(),
                Unit.serializer(),
                CommonErrorMessage.serializer()
            ) {
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get
                    path { using("/auth/idp/${metadata.title}/callback") }
                    params {
                        +boundTo(OpenIdConnectCallback::session_state)
                        +boundTo(OpenIdConnectCallback::code)
                        +boundTo(OpenIdConnectCallback::state)
                    }
                }
            }
        }

        // Invoked by the us on the target server
        val openIdProviderApi = object : CallDescriptionContainer("openidprovider") {
            val token =
                call("callback", Unit.serializer(), OpenIdConnectToken.serializer(), CommonErrorMessage.serializer()) {
                    auth {
                        access = AccessRight.READ
                        roles = Roles.PUBLIC
                    }

                    http {
                        method = HttpMethod.Post
                        path { using(tokenEndpoint.removePrefix(configuration.hostInfo().toString())) }
                        body { bindEntireRequestFromBody() }
                    }
                }
        }

        // Client used for the `openIdProviderApi`.
        val oidcClient = rpcClient.emptyAuth().withFixedHost(configuration.hostInfo())

        microWhichIsConfiguringCalls = micro
        with(server) {
            implement(openIdClientApi.callback) {
                val sctx = ctx as HttpCall

                // The OpenID provider should redirect the end-user's client to this callback. Of course, it is not
                // guaranteed that this isn't simply the end-user being malicious, so we need to verify the
                // communication.

                stateTableMutex.withLock {
                    stateTable[OidcState(request.state)]
                } ?: run {
                    debugSystem.detail("Rejecting OIDC callback: Unknown state")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                // We start by calling back the OpenID provider and ask them to issue us a token based on the code we
                // have just received from the client.
                val tokenResponse = openIdProviderApi.token.call(
                    Unit,
                    oidcClient.withHttpBody(
                        buildString {
                            append("client_id=")
                            append(configuration.client.id)

                            append("&client_secret=")
                            append(configuration.client.secret)

                            append("&code=")
                            append(request.code)

                            append("&grant_type=authorization_code")

                            append("&redirect_uri=")
                            append(ownHost.toStringOmitDefaultPort())
                            append("/auth/idp/${metadata.title}/callback")
                        },
                        ContentType.Application.FormUrlEncoded
                    )
                ).orNull() ?: run {
                    debugSystem.detail("Rejecting OIDC callback: Bad response from token callback")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
                }

                // Assuming that this goes well, we will verify that the tokens also make sense. We do the verification
                // based on certificates which we receive from the configuration.
                val isUsingIdToken = configuration.experimental.tokenToUse ==
                    OpenIdConnectIdpConfig.TokenToUse.id_token

                val tokenToUse = when (configuration.experimental.tokenToUse) {
                    OpenIdConnectIdpConfig.TokenToUse.id_token -> tokenResponse.id_token

                    // NOTE(Dan): Using the access_token is not something you should do according to the spec
                    OpenIdConnectIdpConfig.TokenToUse.access_token -> tokenResponse.access_token
                }

                val jwt = accessTokenValidator.validateOrNull(tokenToUse)?.claims
                    ?: run {
                        if (log.isDebugEnabled) {
                            log.debug("OIDC failed due to a bad token: ${tokenResponse.access_token} ${tokenToUse}")
                            val signing = configuration.signing
                            when (configuration.signing.algorithm) {
                                OpenIdConnectIdpConfig.SignatureType.RS256,
                                OpenIdConnectIdpConfig.SignatureType.ES256, -> {
                                    log.debug(InternalTokenValidationJWT.formatCert(signing.key, true))
                                }
                            }
                        }
                        debugSystem.detail(
                            "Rejecting OIDC callback: Bad token",
                            defaultMapper.encodeToJsonElement(OpenIdConnectToken.serializer(), tokenResponse)
                        )
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                // NOTE(Dan): The JWT has already been validated in terms of the certificate and the time.

                if (isUsingIdToken) {
                    // We verify the following values, just in case. We are not currently following the spec 100% in
                    // terms of what we should verify.
                    // TODO(Dan): Verify that the audience does indeed contain the client-id.
                    val returnedSessionState = jwt["session_state"]?.takeIf { !it.isNull }?.asString()
                    val azp = jwt["azp"]?.takeIf { !it.isNull }?.asString()
                    val aud = runCatching {
                        jwt["aud"]?.takeIf { !it.isNull }?.asList(String::class.java)
                    }.getOrNull() ?: listOfNotNull(jwt["aud"]?.takeIf { !it.isNull }?.asString())

                    if (aud.contains(configuration.client.id) != true) {
                        debugSystem.detail(
                            "Rejecting OIDC callback: Bad session state",
                            defaultMapper.encodeToJsonElement(OpenIdConnectToken.serializer(), tokenResponse)
                        )
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                    if (returnedSessionState != request.session_state) {
                        log.debug("OIDC failed due to bad session state ($returnedSessionState != ${request.session_state}")

                        debugSystem.detail(
                            "Rejecting OIDC callback: Bad session state",
                            defaultMapper.encodeToJsonElement(OpenIdConnectToken.serializer(), tokenResponse)
                        )
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                    if (azp != null && azp != configuration.client.id) {
                        log.debug("OIDC failed due to azp not matching configured client ($azp != ${configuration.client.id}")
                        debugSystem.detail(
                            "Rejecting OIDC callback: Bad azp",
                            defaultMapper.encodeToJsonElement(OpenIdConnectToken.serializer(), tokenResponse)
                        )
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }
                }

                val subject = jwt["sub"]?.takeIf { !it.isNull }?.asString() ?: run {
                    debugSystem.detail(
                        "Rejecting OIDC callback: Missing subject!",
                        defaultMapper.encodeToJsonElement(OpenIdConnectToken.serializer(), tokenResponse)
                    )
                    throw RPCException("Missing sub", HttpStatusCode.BadRequest)
                }

                // These values all originate from 'OpenID Connect Basic Client Implementer's Guide 1.0 - draft 40'
                // in section '2.5 Standard Claims'.
                //
                // Link: https://openid.net/specs/openid-connect-basic-1_0.html
                val preferredUsername = jwt["preferred_username"]?.takeIf { !it.isNull }?.asString()
                val name = jwt["name"]?.takeIf { !it.isNull }?.asString()
                val givenName = jwt["given_name"]?.takeIf { !it.isNull }?.asString()
                val familyName = jwt["family_name"]?.takeIf { !it.isNull }?.asString()
                val middleName = jwt["middle_name"]?.takeIf { !it.isNull }?.asString()
                val nickname = jwt["nickname"]?.takeIf { !it.isNull }?.asString()
                val email = jwt["email"]?.takeIf { !it.isNull }?.asString()
                val emailVerified = jwt["email_verified"]?.takeIf { !it.isNull }?.asBoolean() ?: false
                val phoneNumber = jwt["phone_number"]?.takeIf { !it.isNull }?.asString()
                val phoneNumberVerified = jwt["phone_number_verified"]?.takeIf { !it.isNull }?.asBoolean() ?: false

                loginCompleteFn(
                    metadata.id,
                    subject,
                    givenName,
                    familyName,
                    email,
                    false,
                    null,
                    (ctx as HttpCall).call,
                )

                okContentAlreadyDelivered()
            }
        }
        microWhichIsConfiguringCalls = null
    }

    private val secureRandom = SecureRandom()
    private fun generateToken(): String {
        val array = ByteArray(32)
        secureRandom.nextBytes(array)
        return Base64.getUrlEncoder().encodeToString(array)
    }

    override suspend fun startLogin(call: ApplicationCall) {
        val token = OidcState(generateToken())
        stateTableMutex.withLock {
            stateTable[token] = ConnectionState()
        }

        call.respondRedirect(
            buildString {
                append(authEndpoint)
                append('?')
                append("response_type=code")
                append("&client_id=${configuration.client.id}")
                append("&scope=openid")
                append("&state=")
                append(token.state)
                append("&redirect_uri=")
                append(ownHost.toStringOmitDefaultPort())
                append("/auth/idp/${metadata.title}/callback")
            }
        )
    }
}

private fun RpcClient.emptyAuth(): AuthenticatedClient {
    return AuthenticatedClient(
        this,
        OutgoingHttpCall,
        authenticator = {
            // Do nothing
        },
        afterHook = {
            // Do nothing
        }
    )
}

@Serializable
data class OpenIdConnectCallback(
    val state: String,
    val session_state: String? = null,
    val code: String
)

@Serializable
data class OpenIdConnectToken(
    val access_token: String,
    val id_token: String,
    val refresh_token: String? = null,
    val session_state: String? = null,
)

private fun HostInfo.toStringOmitDefaultPort(): String {
    val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
    return if (isDefaultPort) buildString {
        append(scheme)
        append("://")
        append(host)
    } else {
        toString()
    }
}
