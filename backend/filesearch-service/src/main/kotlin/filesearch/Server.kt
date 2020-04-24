package dk.sdu.cloud.filesearch

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.filesearch.http.SearchController
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger

/**
 * A server for the filesearch-service
 */
class Server(
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        with(micro.server) {
            configureControllers(
                SearchController(client)
            )
        }

        startServices()
    }
}
