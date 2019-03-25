package dk.sdu.cloud.file.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.favorite.http.FileFavoriteController
import dk.sdu.cloud.file.favorite.processors.StorageEventProcessor
import dk.sdu.cloud.file.favorite.services.FileFavoriteHibernateDAO
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = micro.hibernateDatabase

        // Initialize services here
        val fileFavoriteDao = FileFavoriteHibernateDAO()
        val fileFavoriteService = FileFavoriteService(db, fileFavoriteDao, client)

        // Processors
        StorageEventProcessor(fileFavoriteService, micro.eventStreamService).init()

        // Initialize server
        with(micro.server) {
            configureControllers(
                FileFavoriteController(fileFavoriteService, client)
            )
        }

        startServices()
    }
}
