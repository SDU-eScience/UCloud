package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.http.*
import dk.sdu.cloud.plugins.ConnectionResponse
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.provider.api.IntegrationProviderConnectResponse
import dk.sdu.cloud.provider.api.IntegrationProviderRetrieveManifestResponse
import dk.sdu.cloud.sql.DBContext
import h2o.H2O_TOKEN_CONTENT_TYPE
import io.ktor.http.*
import kotlinx.cinterop.pointed
import kotlinx.serialization.encodeToString

class ConnectionController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun H2OServer.configure() {
        if (controllerContext.configuration.serverMode != ServerMode.SERVER) return

        val providerId = controllerContext.configuration.core.providerId
        val plugin = controllerContext.plugins.connection ?: return
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
    }
}
