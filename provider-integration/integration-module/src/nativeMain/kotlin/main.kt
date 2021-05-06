package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.controllers.ComputeController
import dk.sdu.cloud.controllers.Controller
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main() {
    runBlocking {
        /*
            TODO Binary
            ================================================================================================================

            All dependencies should be statically linked to make sure that this is easy to deploy.

         */

        val log = Logger("Main")
        val server = H2OServer()
        val config = IMConfiguration.load()
        val validation = NativeJWTValidation(config.core.certificate!!)
        loadMiddleware(config, validation)

        val client = RpcClient().also { client ->
            OutgoingHttpRequestInterceptor()
                .install(
                    client,
                    FixedOutgoingHostResolver(HostInfo("localhost", "http", 8080))
                )
        }

        val authenticator = RefreshingJWTAuthenticator(
            client,
            JwtRefresher.Provider(config.core.refreshToken),
            becomesInvalidSoon = { accessToken ->
                val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
                (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                    (1000 * 120) >= Time.now()
            }
        )

        val providerClient = authenticator.authenticateClient(OutgoingHttpCall)
        val plugins = PluginLoader(config).load().freeze()
        val pluginContext = SimplePluginContext(providerClient).freeze()
        val controllerContext = ControllerContext(pluginContext, plugins)

        with(server) {
            configureControllers(
                ComputeController(controllerContext),
            )
        }

        server.start()
    }
}

private fun H2OServer.configureControllers(vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }
}
