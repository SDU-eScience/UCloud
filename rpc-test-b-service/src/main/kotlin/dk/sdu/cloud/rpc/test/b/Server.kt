package dk.sdu.cloud.rpc.test.b

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.rpc.test.b.rpc.TestController

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                TestController()
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
