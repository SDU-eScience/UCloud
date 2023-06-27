package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.IdentityProvider
import dk.sdu.cloud.auth.http.LoginResponder
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.debug.DebugSystemFeature
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class IdentityProviderConfiguration {
    // NOTE(Dan): Please make sure that none of these contain secrets in the toString() method.

    @SerialName("wayf")
    @Serializable
    class Wayf() : IdentityProviderConfiguration() {
        override fun toString() = "WAYF"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }
    }
}

data class IdentityProviderMetadata(
    val title: String,
    val configuration: IdentityProviderConfiguration,
    val countsAsMultiFactor: Boolean,
    var id: Int = 0,
    var retrievedAt: Long = 0L,
) {
    fun toApiModel(): IdentityProvider = IdentityProvider(id, title, null)
}

interface ConfiguredIdentityProvider {
    val metadata: IdentityProviderMetadata

    suspend fun startLogin(call: ApplicationCall)
    suspend fun configureRpcServer(server: RpcServer)
    suspend fun configure(metadata: IdentityProviderMetadata, loginCompleteFn: LoginCompleteFn)
}

typealias LoginCompleteFn = suspend (
    idp: Int,
    idpIdentity: String,
    firstNames: String?,
    lastNames: String?,
    email: String?,
    emailVerified: Boolean,
    organization: String?,
    call: ApplicationCall,
) -> Unit

class IdpService(
    private val db: DBContext,

    private val micro: Micro,
    private val ownHost: HostInfo,
) {
    private val rpcClient = micro.client
    private val rpcServer = micro.server
    private val debugSystem = micro.feature(DebugSystemFeature).system

    lateinit var registrationServiceCyclicHack: RegistrationService
    lateinit var loginResponderCyclicHack: LoginResponder
    lateinit var principalServiceCyclicHack: PrincipalService

    private val mutex = Mutex()
    private var cacheEntries = ArrayList<ConfiguredIdentityProvider>()
    private var lastRenewal = 0L

    suspend fun findById(id: Int): IdentityProviderMetadata {
        renewCache()
        return mutex.withLock { cacheEntries.find { it.metadata.id == id } }?.metadata
            ?: error("Unknown IdP: id = $id. $cacheEntries")
    }

    suspend fun findByTitle(title: String): IdentityProviderMetadata {
        renewCache()
        return mutex.withLock { cacheEntries.find { it.metadata.title == title } }?.metadata
            ?: error("Unknown IdP: title = $title. $cacheEntries")
    }

    suspend fun findAll(): List<IdentityProvider> {
        renewCache()
        return mutex.withLock { cacheEntries.map { it.metadata.toApiModel() } }
    }

    suspend fun startLogin(id: Int, call: ApplicationCall) {
        renewCache()
        val provider = mutex.withLock { cacheEntries.find { it.metadata.id == id } } ?: run {
            call.respondRedirect("/")
            return
        }

        provider.startLogin(call)
    }

    private suspend fun onLoginComplete(
        idp: Int,
        idpIdentity: String,
        firstNames: String?,
        lastNames: String?,
        email: String?,
        emailVerified: Boolean,
        organization: String?,
        call: ApplicationCall
    ) {
        val personOrNull = principalServiceCyclicHack.findByIdpAndTrackInfo(
            idp,
            idpIdentity,
            firstNames,
            lastNames,
            email.takeIf { emailVerified },
            organization,
        )
        if (personOrNull != null) {
            loginResponderCyclicHack.handleSuccessfulLogin(call, "web", personOrNull)
            return
        }

        registrationServiceCyclicHack.submitRegistration(
            firstNames,
            lastNames,
            email,
            emailVerified,
            organization,
            idp,
            idpIdentity,
            call
        )
    }

    private suspend fun renewCache(force: Boolean = false) {
        var now = Time.now()
        if (!force && now - lastRenewal < 60_000) return

        mutex.withLock {
            now = Time.now()
            if (!force && now - lastRenewal < 60_000) return

            db.withSession { session ->
                val newEntries = session.sendPreparedStatement(
                    {},
                    """
                        select id, title, configuration, counts_as_multi_factor
                        from auth.identity_providers
                    """
                ).rows.mapNotNull {
                    try {
                        IdentityProviderMetadata(
                            id = it.getInt(0)!!,
                            title = it.getString(1)!!,
                            configuration = defaultMapper.decodeFromString(
                                IdentityProviderConfiguration.serializer(),
                                it.getString(2)!!
                            ),
                            countsAsMultiFactor = it.getBoolean(3)!!,
                            retrievedAt = now
                        )
                    } catch (ex: Throwable) {
                        log.warn(ex.toReadableStacktrace().toString())
                        null
                    }
                }
                for (entry in newEntries) {
                    val existing = cacheEntries.find { it.metadata.id == entry.id }
                    if (existing != null) {
                        existing.configure(entry, ::onLoginComplete)
                    } else {
                        val newIdpService = createConfiguredIdp(entry)
                        newIdpService.configure(entry, ::onLoginComplete)
                        newIdpService.configureRpcServer(rpcServer)
                        cacheEntries.add(newIdpService)
                    }
                }
            }
        }
    }

    private fun createConfiguredIdp(metadata: IdentityProviderMetadata): ConfiguredIdentityProvider {
        return when (metadata.configuration) {
            is OpenIdConnectIdpConfig -> {
                OpenIdConnect(
                    micro,
                    rpcClient,
                    ownHost,
                    debugSystem,
                )
            }

            is IdentityProviderConfiguration.Wayf -> object : ConfiguredIdentityProvider {
                override lateinit var metadata: IdentityProviderMetadata

                override suspend fun configure(metadata: IdentityProviderMetadata, loginCompleteFn: LoginCompleteFn) {
                    this.metadata = metadata
                }

                override suspend fun configureRpcServer(server: RpcServer) {
                    // Nothing additional to configure
                }

                override suspend fun startLogin(call: ApplicationCall) {
                    call.respondRedirect("/auth/saml/login?service=web")
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
