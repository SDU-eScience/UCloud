package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.kubernetes.rpc.*

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
//                AppKubernetesController()
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
