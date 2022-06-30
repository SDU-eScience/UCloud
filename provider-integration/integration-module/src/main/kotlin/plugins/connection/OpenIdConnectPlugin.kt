package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpMethod
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.provider.api.IntegrationControl
import dk.sdu.cloud.provider.api.IntegrationControlApproveConnectionRequest
import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.utils.secureToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

class OpenIdConnectPlugin : ConnectionPlugin {
    private lateinit var ownHost: Host
    private lateinit var configuration: ConfigSchema.Plugins.Connection.OpenIdConnect
    private lateinit var tokenEndpoint: String
    private lateinit var authEndpoint: String
    private val log = Logger("OpenIdConnect")

    private fun ConfigSchema.Plugins.Connection.OpenIdConnect.hostInfo(): HostInfo {
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

    override fun configure(config: ConfigSchema.Plugins.Connection) {
        this.configuration = config as ConfigSchema.Plugins.Connection.OpenIdConnect
        this.tokenEndpoint = this.configuration.endpoints.token.removeSuffix("?").removeSuffix("/")
        this.authEndpoint = this.configuration.endpoints.auth.removeSuffix("?").removeSuffix("/")
    }

    // The state table is used to map a connection attempt to an active OIDC authentication flow.
    private class ConnectionState(val username: String, val connectionId: String)
    @JvmInline private value class OidcState(val state: String)
    private val stateTableMutex = Mutex()
    private val stateTable = HashMap<OidcState, ConnectionState>()

    override suspend fun PluginContext.initialize() {
        if (config.serverMode != ServerMode.Server) return

        ownHost = config.core.hosts.self
            ?: throw IllegalStateException("The OpenIdConnectPlugin requires core.ownHost to be defined!")
    }

    override fun PluginContext.initializeRpcServer(server: RpcServer) {
        // Token validator used to verify tokens returned by the OpenID provider (explained below).
        val accessTokenValidator = InternalTokenValidationJWT.withPublicCertificate(configuration.certificate)

        // Implemented by the integration module (see explanation below)
        val openIdClientApi = object : CallDescriptionContainer("openidclient") {
            val callback = call<OpenIdConnectCallback, Unit, CommonErrorMessage>("callback") {
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get
                    path { using("/connection/oidc-cb") }
                    params {
                        +boundTo(OpenIdConnectCallback::session_state)
                        +boundTo(OpenIdConnectCallback::code)
                        +boundTo(OpenIdConnectCallback::state)
                    }
                }
            }
        }

        // Invoked by the integration module (see explanation below)
        val openIdProviderApi = object : CallDescriptionContainer("openidprovider") {
            val token = call<Unit, OpenIdConnectToken, CommonErrorMessage>("callback") {
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

        with(server) {
            implement(openIdClientApi.callback) {
                val sctx = ctx as HttpCall

                // The OpenID provider should redirect the end-user's client to this callback. Of course, it is not
                // guaranteed that this isn't simply the end-user being malicious, so we need to verify the
                // communication.

                val ucloudIdentity = stateTableMutex.withLock {
                    stateTable[OidcState(request.state)]
                } ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

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
                            append(ownHost.scheme)
                            append("://")
                            append(ownHost.host)
                            append(":")
                            append(ownHost.port)
                            append("/connection/oidc-cb")
                        },
                        ContentType.Application.FormUrlEncoded
                    )
                ).orThrow()

                // Assuming that this goes well, we will verify that the tokens also make sense. We do the verification
                // based on certificates which we receive from the configuration.
                val jwt = accessTokenValidator.validateOrNull(tokenResponse.access_token)?.claims
                    ?: run {
                        log.debug("OIDC failed due to a bad token: ${tokenResponse.access_token}")
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                val subject = run {
                    // NOTE(Dan): The JWT has already been validated in terms of the certificate and the time.

                    // We verify the following values, just in case. We are not currently following the spec 100% in
                    // terms of what we should verify.
                    // TODO(Dan): Verify that the audience does indeed contain the client-id.
                    val returnedSessionState = jwt["session_state"]?.takeIf { !it.isNull }?.asString()
                        ?: throw RPCException("Missing session_state", HttpStatusCode.BadRequest)
                    val type = jwt["typ"]?.takeIf { !it.isNull }?.asString()
                        ?: throw RPCException("Missing typ", HttpStatusCode.BadRequest)
                    val azp = jwt["azp"]?.takeIf { !it.isNull }?.asString()

                    if (returnedSessionState != request.session_state) {
                        log.debug("OIDC failed due to bad session state ($returnedSessionState != ${request.session_state}")
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                    if (azp != null && azp != configuration.client.id) {
                        log.debug("OIDC failed due to azp not matching configured client ($azp != ${configuration.client.id}")
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                    if (!type.equals("Bearer", ignoreCase = true)) {
                        log.debug("OIDC failed due to the type not being equal to Bearer (it was $type)")
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }

                    // We also fetch some additional information which can be of use to the extensions which performs
                    // mapping and/or user creation.
                    val subject = jwt["sub"]?.takeIf { !it.isNull }?.asString()
                        ?: throw RPCException("Missing sub", HttpStatusCode.BadRequest)

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

                    OpenIdConnectSubject(
                        ucloudIdentity.username,
                        subject,
                        preferredUsername,
                        name,
                        givenName,
                        familyName,
                        middleName,
                        nickname,
                        email,
                        emailVerified,
                        phoneNumber,
                        phoneNumberVerified,
                    )
                }

                log.debug("OIDC success! Subject is $subject")

                val result = onConnectionComplete.invoke(configuration.extensions.onConnectionComplete, subject)
                UserMapping.insertMapping(
                    subject.ucloudIdentity,
                    result.uid,
                    this@initializeRpcServer,
                    ucloudIdentity.connectionId
                )

                IntegrationControl.approveConnection.call(
                    IntegrationControlApproveConnectionRequest(subject.ucloudIdentity),
                    rpcClient
                ).orThrow()

                sctx.ktor.call.respondRedirect(configuration.redirectUrl ?: config.core.hosts.ucloud.toString())

                okContentAlreadyDelivered()
            }
        }
    }

    override suspend fun PluginContext.initiateConnection(username: String): ConnectionResponse {
        val token = OidcState(secureToken(32))
        runBlocking {
            stateTableMutex.withLock {
                stateTable[token] = ConnectionState(username, token.state)
            }
        }

        return ConnectionResponse.Redirect(
            buildString {
                append(authEndpoint)
                append('?')
                append("response_type=code")
                append("&client_id=${configuration.client.id}")
                append("&scope=openid")
                append("&state=")
                append(token.state)
                append("&redirect_uri=")
                append(ownHost.scheme)
                append("://")
                append(ownHost.host)
                append(":")
                append(ownHost.port)
                append("/connection/oidc-cb")
            },
            token.state
        )
    }

    override suspend fun PluginContext.mappingExpiration(): Long {
        var acc = 0L
        with (configuration.mappingTimeToLive) {
            acc += days    * (1000L * 60 * 60 * 24)
            acc += hours   * (1000L * 60 * 60)
            acc += minutes * (1000L * 60)
            acc += seconds * (1000L)
        }
        return acc
    }

    override suspend fun PluginContext.requireMessageSigning(): Boolean = configuration.requireSigning

    private companion object Extensions {
        val onConnectionComplete = extension(OpenIdConnectSubject.serializer(), UidAndGid.serializer())
    }
}

fun AuthenticatedClient.emptyAuth(): AuthenticatedClient {
    return AuthenticatedClient(
        client,
        backend,
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
    val session_state: String,
    val code: String
)

@Serializable
data class OpenIdConnectToken(
    val access_token: String,
    val refresh_token: String,
    val session_state: String,
)

@Serializable
data class OpenIdConnectSubject(
    val ucloudIdentity: String,
    val subject: String,
    val preferredUsername: String?,
    val name: String?,
    val givenName: String?,
    val familyName: String?,
    val middleName: String?,
    val nickname: String?,
    val email: String?,
    val emailVerified: Boolean?,
    val phoneNumber: String?,
    val phoneNumberVerified: Boolean?,
)

