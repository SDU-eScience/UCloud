package dk.sdu.cloud.project.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.project.favorite.rpc.*
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteDAO
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteService
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            val db = AsyncDBSessionFactory(micro.databaseConfig)
            val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

            val projectDAO = ProjectFavoriteDAO()
            val projectFavoriteService = ProjectFavoriteService(db, projectDAO, authenticatedClient)

            configureControllers(
                ProjectFavoriteController(projectFavoriteService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
