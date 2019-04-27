package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.kubernetes.rpc.*
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val podService = PodService(DefaultKubernetesClient(), serviceClient)

        podService.initializeListeners()

        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
