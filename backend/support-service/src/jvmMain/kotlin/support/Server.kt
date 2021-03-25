package dk.sdu.cloud.support

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.support.http.SupportController
import dk.sdu.cloud.support.services.TicketService

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val ticketService = TicketService(authenticatedClient)

        with(micro.server) {
            configureControllers(
                SupportController(ticketService)
            )
        }

        log.info("Service started!")
        startServices()
    }
}
