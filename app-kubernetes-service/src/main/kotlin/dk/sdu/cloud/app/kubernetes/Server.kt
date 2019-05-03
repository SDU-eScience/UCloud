package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val podService = PodService(DefaultKubernetesClient(), serviceClient)
        val vncService = VncService(podService)

        podService.initializeListeners()


        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService, vncService)
            )
        }

        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
        ktorEngine.application.routing {
            vncService.install(this)
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
