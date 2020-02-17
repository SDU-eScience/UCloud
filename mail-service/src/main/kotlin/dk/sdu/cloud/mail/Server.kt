package dk.sdu.cloud.mail

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.mail.rpc.*
import dk.sdu.cloud.mail.services.MailService

class Server(private val config: MailConfiguration, override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val mailService = MailService(authenticatedClient, config.fromAddress, config.whitelist)
        with(micro.server) {
            configureControllers(
                MailController(mailService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
