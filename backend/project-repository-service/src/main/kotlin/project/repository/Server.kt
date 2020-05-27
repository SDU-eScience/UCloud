package dk.sdu.cloud.project.repository

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.project.repository.rpc.*
import dk.sdu.cloud.project.repository.services.RepositoryService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        with(micro.server) {
            configureControllers(
                ProjectRepositoryController(
                    RepositoryService(serviceClient),
                    serviceClient,
                    userClientFactory = { accessToken ->
                        ClientAndBackend(micro.client, OutgoingHttpCall).bearerAuth(accessToken)
                    }
                )
            )
        }

        startServices()
    }
}
