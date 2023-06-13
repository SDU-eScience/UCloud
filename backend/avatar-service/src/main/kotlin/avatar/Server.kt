package dk.sdu.cloud.avatar

import dk.sdu.cloud.avatar.http.AvatarController
import dk.sdu.cloud.avatar.services.AvatarStore
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val avatarStore = AvatarStore(db)

        configureControllers(
            AvatarController(avatarStore),
        )

        startServices()
    }
}
