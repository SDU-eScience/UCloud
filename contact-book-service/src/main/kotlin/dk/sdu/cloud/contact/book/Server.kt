package dk.sdu.cloud.contact.book

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.contact.book.rpc.*

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                ContactBookController()
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
