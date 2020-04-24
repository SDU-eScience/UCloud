package dk.sdu.cloud.accounting.storage

import dk.sdu.cloud.accounting.storage.http.StorageUsedController
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val storageAccountingService = StorageAccountingService(client)

        with(micro.server) {
            configureControllers(
                StorageUsedController(storageAccountingService)
            )
        }

        startServices()
    }
}
