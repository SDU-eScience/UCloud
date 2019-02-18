package dk.sdu.cloud.file.gateway

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.gateway.http.FavoriteController
import dk.sdu.cloud.file.gateway.http.FileController
import dk.sdu.cloud.file.gateway.http.SearchController
import dk.sdu.cloud.file.gateway.services.FileAnnotationService
import dk.sdu.cloud.file.gateway.services.UserCloudService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices

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
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val userCloudService = UserCloudService(client)
        val fileAnnotationService = FileAnnotationService()

        with(micro.server) {
            configureControllers(
                FileController(userCloudService, fileAnnotationService),
                FavoriteController(userCloudService, fileAnnotationService),
                SearchController(userCloudService, fileAnnotationService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
