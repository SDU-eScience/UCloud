package dk.sdu.cloud.rpc.test

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.LogFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.rpc.test.rpc.TestController
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.apache.logging.log4j.Level
import org.slf4j.Logger

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        Foo.test()
        log.trace("Server")
        log.debug("Server")
        log.info("Server")
        log.warn("Server")
        log.error("Server")

        micro.feature(LogFeature).configureLevels(mapOf("dk.sdu.cloud.rpc.test.Server" to Level.TRACE))

        Foo.test()
        log.trace("Server")
        log.debug("Server")
        log.info("Server")
        log.warn("Server")
        log.error("Server")

        micro.feature(LogFeature).configureLevels(mapOf("dk.sdu.cloud.rpc.test.Server" to Level.ERROR))

        Foo.test()
        log.trace("Server")
        log.debug("Server")
        log.info("Server")
        log.warn("Server")
        log.error("Server")

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

object Foo : Loggable {
    override val log: Logger = logger()

    fun test() {
        log.trace("Are we trace?")
        log.debug("Are we debug?")
        log.info("Are we info?")
        log.warn("Are we warn?")
        log.error("Are we error?")
    }
}
