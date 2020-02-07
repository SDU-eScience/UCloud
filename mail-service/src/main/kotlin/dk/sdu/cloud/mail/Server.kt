package dk.sdu.cloud.mail

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.mail.rpc.*
import dk.sdu.cloud.mail.services.MailService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val mailService = MailService()
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
