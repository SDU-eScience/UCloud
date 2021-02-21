package dk.sdu.cloud.avatar

import dk.sdu.cloud.avatar.http.AvatarController
import dk.sdu.cloud.avatar.services.AvatarAsyncDao
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    private val db = AsyncDBSessionFactory(micro.databaseConfig)

    override val log = logger()

    override fun start() {
        val avatarDao = AvatarAsyncDao()
        val completedJobsService = AvatarService(db, avatarDao)

        // Initialize server
        with(micro.server) {
            configureControllers(
                AvatarController(completedJobsService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
