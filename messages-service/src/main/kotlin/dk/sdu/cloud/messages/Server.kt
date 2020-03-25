package dk.sdu.cloud.messages

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.messages.rpc.*

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                MessagesController()
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
