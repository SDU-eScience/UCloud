package dk.sdu.cloud.file.trash

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.trash.http.FileTrashController
import dk.sdu.cloud.file.trash.services.TrashDirectoryService
import dk.sdu.cloud.file.trash.services.TrashService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingWSCall)
        val trashDirectoryService = TrashDirectoryService(client)
        val trashService = TrashService(trashDirectoryService)
        with(micro.server) {
            configureControllers(
                FileTrashController(client, trashService)
            )
        }

        startServices()
    }
}
