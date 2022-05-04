package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.http.*
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequestBlocking
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import platform.posix.*
import kotlinx.coroutines.runBlocking

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
    val browse = browseHandler<PaginationRequestV2, PageV2<ConnectionEntry>>()
    val registerConnection = updateHandler<ConnectionEntry, Unit>("registerConnection")
    val removeConnection = updateHandler<FindByUsername, Unit>("removeConnection")
    val registerSessionProxy = updateHandler<OpenSession.Shell, Unit>("registerSessionProxy")
}

@Serializable
data class TicketRequest(
    val ticket: String
)

class ConnectionController(
    private val controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
) : Controller {
    // Mutex used for configuration and launching of new IM/user instances (See `im.init` in this file)
    private val uimMutex = Mutex()
    private val uimLaunched = HashSet<String>()

    override fun configureIpc(server: IpcServer) {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return
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
            if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            UserMapping.browseMappings(request)
        })

        server.addHandler(ConnectionIpc.registerConnection.handler { user, request ->
            if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            UserMapping.insertMapping(request.username, request.uid, controllerContext.pluginContext, null)

            IntegrationControl.approveConnection.callBlocking(
                IntegrationControlApproveConnectionRequest(request.username),
                controllerContext.pluginContext.rpcClient
            ).orThrow()
        })

        server.addHandler(ConnectionIpc.removeConnection.handler { user, request ->
            if (user.uid != 0U) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            UserMapping.clearMappingByUCloudId(request.username)
        })
    }

    override fun RpcServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return
        val config = controllerContext.configuration

        val providerId = config.core.providerId
        val plugin = config.plugins.connection ?: return
        val pluginContext = controllerContext.pluginContext

        with(pluginContext) {
            with(plugin) {
                initializeRpcServer(this@configure)
            }
        }

        val im = IntegrationProvider(providerId)
        val baseContext = "/ucloud/$providerId/integration/instructions"
        val instructions = object : CallDescriptionContainer(im.namespace + ".instructions") {
            val retrieveInstructions = call<TicketRequest, Unit, CommonErrorMessage>("retrieveInstructions") {
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
            val server = (ctx.serverContext as? HttpContext)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

            with(pluginContext) {
                with(plugin) {
                    val html = showInstructions(mapOf("ticket" to listOf(request.ticket))).html

                    server.session.sendHttpResponseWithData(
                        200,
                        listOf(Header("Content-Type", "text/html")),
                        html.encodeToByteArray()
                    )
                }
            }

            OutgoingCallResponse.AlreadyDelivered()
        }

        implement(im.connect) {
            with(pluginContext) {
                with(plugin) {
                    when (val result = initiateConnection(request.username)) {
                        is ConnectionResponse.Redirect -> {
                            OutgoingCallResponse.Ok(IntegrationProviderConnectResponse(result.redirectTo))
                        }
                        is ConnectionResponse.ShowInstructions -> {
                            OutgoingCallResponse.Ok(
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

            OutgoingCallResponse.Ok(
                IntegrationProviderRetrieveManifestResponse(
                    enabled = true,
                    expireAfterMs = expiration
                )
            )
        }

        implement(im.init) {
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
                    val uimPid = startProcess(
                        listOf(
                            "/usr/bin/sudo",
                            "-u",
                            "#${uid}",
                            controllerContext.ownExecutable,
                            "user",
                            allocatedPort.toString()
                        ),
                        createStreams = {
                            val devnull = NativeFile.open("/dev/null", readOnly = false)
                            unlink("/tmp/ucloud_${uid}.log")
                            val logFile = NativeFile.open(logFilePath, readOnly = false)
                            ProcessStreams(devnull.fd, logFile.fd, logFile.fd)
                        }
                    )

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

            OutgoingCallResponse.Ok(Unit)
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
    fun localIdToUCloudId(localId: Int): String? {
        var result: String? = null

        dbConnection.withTransaction { session ->
            session.prepareStatement(
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

    fun browseMappings(request: PaginationRequestV2): PageV2<ConnectionEntry> {
        val items = ArrayList<ConnectionEntry>()
        dbConnection.withSession { session ->
            session.prepareStatement(
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

    fun ucloudIdToLocalId(ucloudId: String): Int? {
        var result: Int? = null

        dbConnection.withTransaction { session ->
            session.prepareStatement(
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

    fun ucloudIdToLocalIdBulk(ucloudIds: List<String>): Map<String, Int> {
        val result = HashMap<String, Int>()
        dbConnection.withTransaction { session ->
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

    fun insertMapping(
        ucloudId: String,
        localId: Int,
        pluginContext: PluginContext,
        expiry: Long?,
        ctx: DBContext = dbConnection
    ) {
        ctx.withSession { session ->
            session.prepareStatement(
                """
                    insert or replace into user_mapping (ucloud_id, local_identity)
                    values (:ucloud_id, :local_id)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("ucloud_id", ucloudId)
                    bindString("local_id", localId.toString())
                },
            )
        }

        with(pluginContext) {
            val projectPlugin = config.plugins.projects
            if (projectPlugin != null) {
                with(projectPlugin) {
                    runBlocking { onUserMappingInserted(ucloudId, localId) }
                }
            }
        }
    }

    fun clearMappingByUCloudId(ucloudId: String) {
        dbConnection.withTransaction { session ->
            session.prepareStatement(
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

    fun clearMappingByLocalId(localId: Int) {
        dbConnection.withTransaction { session ->
            session.prepareStatement(
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

const val UCLOUD_IM_PORT = 42000
@SharedImmutable
private val portAllocator = atomic(UCLOUD_IM_PORT + 1)