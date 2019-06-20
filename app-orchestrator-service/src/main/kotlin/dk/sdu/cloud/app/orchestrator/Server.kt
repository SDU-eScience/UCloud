package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.orchestrator.rpc.*

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                AppOrchestratorController()
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
