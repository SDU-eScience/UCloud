package dk.sdu.cloud.pong

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.pong.rpc.*
import dk.sdu.cloud.pong.services.StreamTest
import kotlinx.coroutines.runBlocking

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                PongController()
            )
        }

        val job = StreamTest(micro.eventStreamService).startTest()

        startServices()
        runBlocking {
            job.join()
        }
    }
}
