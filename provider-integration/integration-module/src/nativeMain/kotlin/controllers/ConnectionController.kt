package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.http.*
import dk.sdu.cloud.plugins.ConnectionResponse
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.provider.api.IntegrationProviderConnectResponse
import dk.sdu.cloud.provider.api.IntegrationProviderRetrieveManifestResponse
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withTransaction
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.ProcessStreams
import dk.sdu.cloud.utils.startProcess
import h2o.H2O_TOKEN_CONTENT_TYPE
import io.ktor.http.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.system.exitProcess

class ConnectionController(
    private val controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
) : Controller {
    override fun H2OServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.Server) return

        val providerId = controllerContext.configuration.core.providerId
        val plugin = controllerContext.plugins.connection ?: return
        val mapperPlugin = controllerContext.plugins.identityMapper ?: return
        val pluginContext = controllerContext.pluginContext

        val calls = IntegrationProvider(providerId)
        val baseContext = "/ucloud/$providerId/integration/instructions"
        val instructions = object : CallDescriptionContainer(calls.namespace + ".instructions") {
            val retrieveInstructions = call<Unit, Unit, CommonErrorMessage>("retrieveInstructions") {
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get
                    path {
                        using(baseContext)
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
            val req = server.reqPtr.pointed

            val parsed = ParsedQueryString.parse(req.readQuery() ?: "")

            with(pluginContext) {
                with(plugin) {
                    val html = showInstructions(parsed.attributes).html

                    req.res.status = 200
                    req.addHeader(H2O_TOKEN_CONTENT_TYPE, HeaderValues.contentTypeTextHtml)
                    h2o_send_inline(server.reqPtr, html)
                }
            }

            OutgoingCallResponse.AlreadyDelivered()
        }

        implement(calls.init) {
            val envoyConfig = envoyConfig ?: error("No envoy")
            val db = dbConnection ?: error("No db")
            var localId: String? = null
            db.withTransaction { conn ->
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

            val allocatedPort = portAllocator.getAndIncrement()
            envoyConfig.requestConfiguration(
                EnvoyRoute(request.username, request.username),
                EnvoyCluster.create(
                    request.username,
                    "127.0.0.1",
                    allocatedPort
                )
            )

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
                    val logFile = NativeFile.open("/tmp/ucloud_${uid}.log", readOnly = false)
                    ProcessStreams(devnull.fd, logFile.fd, logFile.fd)
                }
            )

            OutgoingCallResponse.Ok(Unit)
        }
    }
}

const val UCLOUD_IM_PORT = 42000
@SharedImmutable
private val portAllocator = atomic(UCLOUD_IM_PORT + 1)