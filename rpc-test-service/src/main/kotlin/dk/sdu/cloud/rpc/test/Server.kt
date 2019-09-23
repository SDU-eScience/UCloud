package dk.sdu.cloud.rpc.test

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.rpc.test.rpc.TestController
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            configureControllers(
                TestController(
                    micro.authenticator.authenticateClient(OutgoingHttpCall),
                    micro.authenticator.authenticateClient(OutgoingWSCall)
                )
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
