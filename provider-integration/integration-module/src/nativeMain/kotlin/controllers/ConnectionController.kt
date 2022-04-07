package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.http.*
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.ConnectionResponse
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.provider.api.IntegrationProviderConnectResponse
import dk.sdu.cloud.provider.api.IntegrationProviderRetrieveManifestResponse
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.ProcessStreams
import dk.sdu.cloud.utils.startProcess
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import platform.posix.*

object ConnectionIpc : IpcContainer("connections") {
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
    }

    override fun RpcServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return

        val providerId = controllerContext.configuration.core.providerId
        val plugin = controllerContext.plugins.connection ?: return
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
                val devInstance = controllerContext.configuration.core.developmentInstance
                val allocatedPort = devInstance?.port ?: portAllocator.getAndIncrement()

                // Launch the IM/User instance
                if (devInstance?.userId != uid) {
                    startProcess(
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
                            val logFile = NativeFile.open("/tmp/ucloud_${uid}.log", readOnly = false)
                            ProcessStreams(devnull.fd, logFile.fd, logFile.fd)
                        }
                    )
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

    fun insertMapping(
        ucloudId: String,
        localId: Int,
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