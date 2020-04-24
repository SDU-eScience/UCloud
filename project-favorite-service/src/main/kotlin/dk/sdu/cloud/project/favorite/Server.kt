package dk.sdu.cloud.project.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.project.favorite.rpc.*
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteHibernateDAO
import dk.sdu.cloud.project.favorite.services.ProjectFavoriteService
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestUsers
import kotlinx.coroutines.runBlocking

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(micro.server) {
            val db = micro.hibernateDatabase
            val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

            val projectHibernateDAO = ProjectFavoriteHibernateDAO()
            val projectFavoriteService = ProjectFavoriteService(db, projectHibernateDAO, authenticatedClient)
            
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
