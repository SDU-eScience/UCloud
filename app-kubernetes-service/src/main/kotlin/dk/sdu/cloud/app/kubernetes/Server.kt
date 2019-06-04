package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.AuthenticationService
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.TunnelManager
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.install
import io.ktor.routing.routing

class Server(override val micro: Micro, private val configuration: Configuration) : CommonServer {
    override val log = logger()
    lateinit var tunnelManager: TunnelManager

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val podService = PodService(
            DefaultKubernetesClient(),
            serviceClient,
            appRole = if (micro.developmentModeEnabled) {
                "sducloud-app-dev"
            } else {
                "sducloud-app"
            }
        )

        val authenticationService = AuthenticationService(serviceClient, micro.tokenValidation)
        tunnelManager = TunnelManager(podService)
        tunnelManager.install()

        val vncService = VncService(tunnelManager)
        val webService = WebService(
            authenticationService = authenticationService,
            tunnelManager = tunnelManager,
            performAuthentication = configuration.performAuthentication,
            prefix = configuration.prefix,
            domain = configuration.domain,
            cookieName = configuration.cookieName
        )

        podService.initializeListeners()

        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService, vncService, webService)
            )
        }

        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
        ktorEngine.application.install(io.ktor.websocket.WebSockets)
        ktorEngine.application.routing {
            vncService.install(this)
            webService.install(this)
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        tunnelManager.shutdown()
    }
}
