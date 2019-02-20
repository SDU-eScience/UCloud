package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.http.ManagementController
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {

    override val log = logger()

    override fun start() {
        // Initialize services here

        // Initialize consumers here:
        // addConsumers(...)

        // Initialize server

        with(micro.server) {
            configureControllers(
                ManagementController()
            )
        }

        startServices()
    }
}
