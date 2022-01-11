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
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.sql.withTransaction
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.ProcessStreams
import dk.sdu.cloud.utils.startProcess
import io.ktor.http.*
import io.ktor.http.HttpMethod
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
    private val mapMutex = Mutex()
    private val mappedUsersToPort = HashMap<String, Int>()

    override fun configureIpc(server: IpcServer) {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return
        val idMapper = controllerContext.plugins.identityMapper ?: return
        val envoyConfig = envoyConfig ?: return

        server.addHandler(ConnectionIpc.registerSessionProxy.handler { user, request ->
            val ucloudIdentity = with(controllerContext.pluginContext) {
                with(idMapper) {
                    runCatching {
                        lookupUCloudIdentifyFromLocalIdentity(
                            mapUidToLocalIdentity(user.uid.toInt())
                        )
                    }.getOrNull() ?: throw RPCException("Unknown user", HttpStatusCode.Forbidden)
                }
            }

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
        val mapperPlugin = controllerContext.plugins.identityMapper ?: return
        val pluginContext = controllerContext.pluginContext

        val calls = IntegrationProvider(providerId)
        val baseContext = "/ucloud/$providerId/integration/instructions"
        val instructions = object : CallDescriptionContainer(calls.namespace + ".instructions") {
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

        implement(calls.connect) {
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

        implement(calls.retrieveManifest) {
            OutgoingCallResponse.Ok(
                IntegrationProviderRetrieveManifestResponse(
                    enabled = true
                )
            )
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

        implement(calls.init) {
            val envoyConfig = envoyConfig ?: error("No envoy")
            var localId: String? = null
            dbConnection.withTransaction { conn ->
                conn.prepareStatement(
                    //language=SQLite
                    "select local_identity from user_mapping where ucloud_id = :ucloud_id"
                ).useAndInvoke({ bindString("ucloud_id", request.username) }) { row ->
                    localId = row.getString(0)
                }
            }

            val capturedId = localId ?: throw RPCException("Unknown user", HttpStatusCode.BadRequest)

            val (uid) = with(pluginContext) {
                with(mapperPlugin) {
                    mapLocalIdentityToUidAndGid(capturedId)
                }
            }

            val devInstance = controllerContext.configuration.core.developmentInstance
            val allocatedPort = devInstance?.port ?: portAllocator.getAndIncrement()
            envoyConfig.requestConfiguration(
                EnvoyRoute.Standard(request.username, request.username),
                EnvoyCluster.create(
                    request.username,
                    "127.0.0.1",
                    allocatedPort
                )
            )

            mapMutex.withLock {
                mappedUsersToPort[request.username] = allocatedPort
            }

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

            OutgoingCallResponse.Ok(Unit)
        }
    }
}

fun lookupUCloudIdentifyFromLocalIdentity(localId: String): String? {
    var result: String? = null
    dbConnection.withSession { session ->
        session.prepareStatement(
            //language=SQLite
            """
                    select ucloud_id 
                    from user_mapping 
                    where local_identity = :local_id
                """
        ).useAndInvoke(
            prepare = {
                bindString("local_id", localId)
            },
            readRow = { row ->
                result = row.getString(0)!!
            }
        )
    }
    return result
}

const val UCLOUD_IM_PORT = 42000
@SharedImmutable
private val portAllocator = atomic(UCLOUD_IM_PORT + 1)
