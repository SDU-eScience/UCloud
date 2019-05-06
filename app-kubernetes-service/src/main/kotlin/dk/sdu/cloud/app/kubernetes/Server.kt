package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.routing.routing

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

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

        val vncService = VncService(podService)
        val webService = WebService(
            podService,
            domain = "127.0.0.1.xip.io"
        )

        podService.initializeListeners()

        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService, vncService, webService)
            )
        }

        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
        ktorEngine.application.routing {
            vncService.install(this)
            webService.install(this)
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
