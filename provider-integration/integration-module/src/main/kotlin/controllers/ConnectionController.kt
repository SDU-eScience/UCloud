package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.app.orchestrator.api.SSHKeysProvider
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpMethod
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class ConnectionEntry(
    val username: String,
    val uid: Int,
)

@Serializable
data class FindByUsername(
    val username: String
)

object ConnectionIpc : IpcContainer("connections") {
    val browse = browseHandler(PaginationRequestV2.serializer(), PageV2.serializer(ConnectionEntry.serializer()))
    val registerConnection = updateHandler("registerConnection", ConnectionEntry.serializer(), Unit.serializer())
    val removeConnection = updateHandler("removeConnection", FindByUsername.serializer(), Unit.serializer())
    val registerSessionProxy = updateHandler("registerSessionProxy", OpenSession.Shell.serializer(), Unit.serializer())
}

object MessageSigningIpc : IpcContainer("rpcsigning") {
    val browse = browseHandler(Unit.serializer(), MessageSigningIpcBrowseResponse.serializer())
}

@Serializable
data class MessageSigningIpcBrowseResponse(
    val keys: List<MessageSigningKeyStore.KeyInfo>
)

@Serializable
data class TicketRequest(
    val ticket: String
)

@Serializable
data class SigningKeyRequest(
    val publicKey: String,
)

@Serializable
data class SigningKeyResponse(
    val redirectTo: String
)

class ConnectionController(
    private val controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
) : Controller, IpcController {
    // Mutex used for configuration and launching of new IM/user instances (See `im.init` in this file)
    private val uimMutex = Mutex()
    private val uimLaunched = HashSet<String>()

    override fun configureIpc(server: IpcServer) {
        if (!controllerContext.configuration.shouldRunServerCode()) return
        val envoyConfig = envoyConfig ?: return

        server.addHandler(ConnectionIpc.registerSessionProxy.handler { user, request ->
            val ucloudIdentity = UserMapping.localIdToUCloudId(user.uid.toInt())
                ?: throw RPCException("Unknown user", HttpStatusCode.Forbidden)

            envoyConfig.requestConfiguration(
                EnvoyRoute.ShellSession(
                    request.sessionIdentifier,
                    controllerContext.configuration.core.providerId,
                    ucloudIdentity
                ),
                null,
            )
        })

        server.addHandler(ConnectionIpc.browse.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            UserMapping.browseMappings(request)
        })

        server.addHandler(ConnectionIpc.registerConnection.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            UserMapping.insertMapping(request.username, request.uid, controllerContext.pluginContext, null)

            IntegrationControl.approveConnection.callBlocking(
                IntegrationControlApproveConnectionRequest(request.username),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        })

        server.addHandler(ConnectionIpc.removeConnection.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            UserMapping.clearMappingByUCloudId(request.username)
        })

        server.addHandler(MessageSigningIpc.browse.handler { user, request ->
            MessageSigningIpcBrowseResponse(MessageSigningKeyStore.browseKeys(user.uid.toInt()))
        })
    }

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        val config = controllerContext.configuration

        val providerId = config.core.providerId
        val plugin = config.plugins.connection ?: return
        val pluginContext = controllerContext.pluginContext

        if (config.shouldRunUserCode()) {
            val sshApi = SSHKeysProvider(providerId)
            implement(sshApi.onKeyUploaded) {
                with(pluginContext) {
                    with(plugin) {
                        onSshKeySynchronized(request.username, request.allKeys)
                    }
                }
            }
        }

        if (config.shouldRunServerCode()) {
            with(pluginContext) {
                with(plugin) {
                    initializeRpcServer(rpcServer)
                }
            }

            val im = IntegrationProvider(providerId)
            val baseContext = "/ucloud/$providerId/integration/instructions"
            val instructions = object : CallDescriptionContainer(im.namespace + ".instructions") {
                val retrieveInstructions = call(
                    "retrieveInstructions",
                    TicketRequest.serializer(),
                    Unit.serializer(),
                    CommonErrorMessage.serializer()
                ) {
                    auth {
                        access = AccessRight.READ
                        roles = Roles.PUBLIC
                    }

                    http {
                        method = HttpMethod.Get
                        path {
                            using(baseContext)
                        }
                        params {
                            +boundTo(TicketRequest::ticket)
                        }
                    }
                }
            }

            implement(instructions.retrieveInstructions) {
                val server = (ctx as? HttpCall)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

                with(requestContext(controllerContext)) {
                    with(plugin) {
                        val html = showInstructions(mapOf("ticket" to listOf(request.ticket))).html

                        server.ktor.call.respondText(html, ContentType.Text.Html)
                    }
                }

                okContentAlreadyDelivered()
            }

            val redirectProxy = object : CallDescriptionContainer("${im.namespace}.redirector") {
                val redirect = call(
                    "redirect",
                    SigningKeyRequest.serializer(),
                    SigningKeyResponse.serializer(),
                    CommonErrorMessage.serializer()
                ) {
                    httpUpdate("/ucloud/$providerId/integration", "redirect", Roles.PUBLIC)
                }
            }

            implement(redirectProxy.redirect) {
                val httpContext = ctx as HttpCall

                val sessionId = httpContext.ktor.call.request.queryParameters["session"]
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                val session = MessageSigningKeyStore._redirectCache.get(sessionId)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                val keyId = MessageSigningKeyStore.createKey(session.ucloudUser, request.publicKey)
                session.keyId = keyId

                session.beforeRedirect()
                ok(SigningKeyResponse(session.redirectTo))
            }

            implement(im.connect) {
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        val requireSigning = requireMessageSigning()
                        when (val result = initiateConnection(request.username)) {
                            is ConnectionResponse.Redirect -> {
                                if (requireSigning) {
                                    val redirectToken = secureToken(32)

                                    MessageSigningKeyStore._redirectCache.insert(
                                        redirectToken,
                                        RedirectEntry(
                                            request.username,
                                            result.redirectTo,
                                            result.globallyUniqueConnectionId,
                                            result.beforeRedirect
                                        )
                                    )

                                    // NOTE(Dan): This gives UCloud/Core a valid session for uploading a public key, thus
                                    // it is a direct requirement that UCloud/Core isn't able to authenticate itself with
                                    // the redirect which follows the key upload!
                                    ok(
                                        IntegrationProviderConnectResponse(
                                            "/ucloud/$providerId/integration/redirect?session=${redirectToken}"
                                        )
                                    )
                                } else {
                                    result.beforeRedirect()
                                    ok(IntegrationProviderConnectResponse(result.redirectTo))
                                }
                            }

                            is ConnectionResponse.ShowInstructions -> {
                                if (requireSigning) {
                                    error(
                                        "Plugin has returned ShowInstructions but signing is required. " +
                                            "This is not supported! Only redirect is supported when signing is required."
                                    )
                                }

                                ok(
                                    IntegrationProviderConnectResponse(
                                        baseContext + encodeQueryParamsToString(result.query)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            implement(im.retrieveManifest) {
                val expiration = with(plugin) {
                    with(pluginContext) {
                        mappingExpiration()
                    }
                }

                val requiresMessageSigning = with(plugin) {
                    with(pluginContext) {
                        requireMessageSigning()
                    }
                }

                ok(
                    IntegrationProviderRetrieveManifestResponse(
                        enabled = true,
                        expireAfterMs = expiration,
                        requiresMessageSigning = requiresMessageSigning,
                    )
                )
            }

            implement(im.init) {
                if (!config.core.launchRealUserInstances) {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }

                // NOTE(Dan): This code is responsible for launching new instances of IM/User.

                // First we map the UCloud username to a local UID.
                // If no such user exists, notify UCloud/Core that the user is not known to this provider
                val envoyConfig = envoyConfig ?: error("No envoy")
                val uid = UserMapping.ucloudIdToLocalId(request.username)
                    ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)

                // Obtain the global lock for IM/User configuration
                uimMutex.withLock {
                    // Check if we were not the first to acquire the lock
                    if (request.username in uimLaunched) return@withLock
                    uimLaunched.add(request.username)

                    // Allocate a port, if the IM/User does not have a static port allocated.
                    // Static ports are used only for development purposes.
                    val devInstances = config.server.developmentMode.predefinedUserInstances
                    val devInstance = devInstances.find { it.username == request.username }
                    val allocatedPort = devInstance?.port ?: portAllocator.getAndIncrement()

                    // Launch the IM/User instance
                    if (devInstance == null) {
                        val logFilePath = controllerContext.configuration.core.logs.directory + "/user_${uid}.log"
                        val ownExecutable =
                            if (controllerContext.ownExecutable.endsWith("/java")) "/usr/bin/ucloud"
                            else controllerContext.ownExecutable
                        val uimPid = ProcessBuilder()
                            .command(
                                "/usr/bin/sudo",
                                "-u",
                                "#${uid}",
                                ownExecutable,
                                "user",
                                allocatedPort.toString()
                            )
                            .redirectOutput(ProcessBuilder.Redirect.appendTo(File("/tmp/ucloud_${uid}.log")))
                            .redirectError(ProcessBuilder.Redirect.appendTo(File("/tmp/ucloud_${uid}.log")))
                            .start()

                        uimPid.inputStream.close()

                        // NOTE(Dan): We do not wish to kill this process on exit, since we do not have permissions to
                        // kill it. This is instead handled by the ping+pong protocol.
                        ProcessWatcher.addWatch(uimPid, killOnExit = false) { statusCode ->
                            log.warn("IM/User for uid=$uid terminated unexpectedly with statusCode=$statusCode")
                            log.warn("You might be able to find more information in the log file: $logFilePath")
                            log.warn("The instance will be automatically restarted when the user makes another request")
                            uimMutex.withLock {
                                uimLaunched.remove(request.username)
                            }
                        }
                    }

                    // Configure Envoy to route the relevant IM/User traffic to the newly launched instance
                    envoyConfig.requestConfiguration(
                        EnvoyRoute.Standard(request.username, request.username),
                        EnvoyCluster.create(
                            request.username,
                            "127.0.0.1",
                            allocatedPort
                        )
                    )
                }

                ok(Unit)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

/**
 * The UserMapping service is responsible for storing a mapping between UCloud identities and local identities.
 *
 * This service is only usable by when the IM is running in server mode.
 *
 * Local identities are _always_ represented by a Unix UID. Plugins and services which require knowledge of the local
 * UID should query this service.
 */
object UserMapping {
    suspend fun localIdToUCloudId(localId: Int): String? {
        var result: String? = null

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select ucloud_id
                    from user_mapping
                    where local_identity = :local_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("local_id", localId.toString())
                },
                readRow = { row ->
                    result = row.getString(0)!!
                }
            )
        }

        return result
    }

    suspend fun browseMappings(request: PaginationRequestV2): PageV2<ConnectionEntry> {
        val items = ArrayList<ConnectionEntry>()
        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select ucloud_id, local_identity
                    from user_mapping
                """
            ).useAndInvoke(
                prepare = {},
                readRow = { row ->
                    val ucloudUsername = row.getString(0)!!
                    val uid = row.getString(1)!!.toIntOrNull()
                    if (uid != null) {
                        items.add(ConnectionEntry(ucloudUsername, uid))
                    }
                }
            )
        }

        return PageV2(items.size, items, null)
    }

    suspend fun ucloudIdToLocalId(ucloudId: String): Int? {
        var result: Int? = null

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select local_identity
                    from user_mapping
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_id", ucloudId)
                },
                readRow = { row ->
                    result = row.getString(0)!!.toIntOrNull()
                }
            )
        }

        return result
    }

    suspend fun ucloudIdToLocalIdBulk(ucloudIds: List<String>): Map<String, Int> {
        val result = HashMap<String, Int>()
        dbConnection.withSession { session ->
            val queryTable = ucloudIds.map { mapOf("ucloud_id" to it) }
            session.prepareStatement(
                """
                    with query as (
                        ${safeSqlTableUpload("query", queryTable)}
                    )
                    select ucloud_id, local_identity
                    from
                        user_mapping um join
                        query q on um.ucloud_id = q.ucloud_id
                """
            ).useAndInvoke(
                prepare = { bindTableUpload("query", queryTable) },
                readRow = { row ->
                    result[row.getString(0)!!] = row.getString(1)!!.toInt()
                }
            )
        }
        return result
    }

    suspend fun insertMapping(
        ucloudId: String,
        localId: Int,
        pluginContext: PluginContext,
        pluginConnectionId: String?,
        ctx: DBContext = dbConnection
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into user_mapping (ucloud_id, local_identity)
                    values (:ucloud_id, :local_id)
                    on conflict (ucloud_id) do update set ucloud_id = :ucloud_id, local_identity = :local_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("ucloud_id", ucloudId)
                    bindString("local_id", localId.toString())
                },
            )

            if (pluginConnectionId != null) {
                MessageSigningKeyStore.activateKey(pluginConnectionId, session)
            }
        }

        with(pluginContext) {
            val projectPlugin = config.plugins.projects
            if (projectPlugin != null) {
                with(projectPlugin) {
                    runBlocking { onUserMappingInserted(ucloudId, localId) }
                }
            }
        }

        pluginContext.config.plugins.temporary.onConnectionCompleteHandlers.forEach { handler ->
            handler(ucloudId, localId)
        }

    }

    suspend fun clearMappingByUCloudId(ucloudId: String) {
        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from user_mapping
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("ucloud_id", ucloudId)
                },
            )
        }
    }

    suspend fun clearMappingByLocalId(localId: Int) {
        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from user_mapping
                    where local_identity = :local_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("local_id", localId.toString())
                },
            )
        }
    }
}

object MessageSigningKeyStore {
    suspend fun clearMapping(performedBy: Int, mapping: Int, ctx: DBContext = dbConnection) {
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    delete from message_signing_key
                    where
                        ucloud_user in (
                            select ucloud_id
                            from user_mapping
                            where local_identity = :performed_by
                        ) and
                        id = :mapping; 
                """
            ).useAndInvokeAndDiscard {
                bindInt("performed_by", performedBy)
                bindInt("mapping", mapping)
            }
        }
    }

    suspend fun lookupKeys(ucloudUser: String, ctx: DBContext = dbConnection): List<String> {
        val result = ArrayList<String>()
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select public_key
                    from message_signing_key
                    where ucloud_user = :ucloud_user;
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_user", ucloudUser)
                },
                readRow = { row ->
                    result.add(row.getString(0)!!)
                },
            )
        }
        return result
    }

    suspend fun createKey(ucloudUser: String, key: String, ctx: DBContext = dbConnection): Int {
        var result: Int? = null
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into message_signing_key (public_key, ucloud_user)
                    values (:public_key, :ucloud_user)
                    returning id;
                """
            ).useAndInvoke(
                prepare = {
                    bindString("public_key", key)
                    bindString("ucloud_user", ucloudUser)
                },
                readRow = { row ->
                    result = row.getInt(0)
                },
            )
        }

        return result ?: error("Unable to create a message signing key. Is the database corrupt?")
    }

    suspend fun activateKey(pluginConnectionId: String, ctx: DBContext = dbConnection) {
        val keyId = _redirectCache.findOrNull { it.pluginId == pluginConnectionId }?.keyId
            ?: return

        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    update message_signing_key
                    set is_key_active = true
                    where id = :mapping;
                """
            ).useAndInvokeAndDiscard {
                bindInt("mapping", keyId)
            }
        }
    }

    @Serializable
    data class KeyInfo(val createdAt: Long, val id: Int, val key: String)

    suspend fun browseKeys(performedBy: Int, ctx: DBContext = dbConnection): List<KeyInfo> {
        val result = ArrayList<KeyInfo>()
        ctx.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    select extract(epoch from ts) as bigint, id, public_key
                    from message_signing_key
                    where
                        ucloud_user in (
                            select ucloud_id
                            from user_mapping
                            where local_identity = :performed_by
                        );
                """
            ).useAndInvoke(
                prepare = {
                    bindString("performed_by", performedBy.toString())
                },
                readRow = { row ->
                    result.add(
                        KeyInfo(
                            row.getLong(0)!! * 1000,
                            row.getInt(1)!!,
                            row.getString(2)!!
                        )
                    )
                },
            )
        }
        return result
    }

    val _redirectCache = SimpleCache<String, RedirectEntry>(
        maxAge = 1000L * 60 * 15,
        lookup = { null }
    )
}

data class RedirectEntry(
    val ucloudUser: String,
    val redirectTo: String,
    val pluginId: String,
    val beforeRedirect: suspend () -> Unit,
    var keyId: Int? = null
)

private val portAllocator = AtomicInteger(UCLOUD_IM_PORT + 1)
