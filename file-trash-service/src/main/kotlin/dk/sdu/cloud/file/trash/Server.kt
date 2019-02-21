package dk.sdu.cloud.file.trash

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.trash.http.FileTrashController
import dk.sdu.cloud.file.trash.services.TrashDirectoryService
import dk.sdu.cloud.file.trash.services.TrashService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro
) : CommonServer {
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

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

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
