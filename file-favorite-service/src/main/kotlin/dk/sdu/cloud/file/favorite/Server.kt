package dk.sdu.cloud.file.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.favorite.http.FileFavoriteController
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val fileFavoriteService = FileFavoriteService(client)

        with(micro.server) {
            configureControllers(
                FileFavoriteController(fileFavoriteService, client.withoutAuthentication())
            )
        }

        startServices()
    }
}
