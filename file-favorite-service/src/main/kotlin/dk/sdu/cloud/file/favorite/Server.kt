package dk.sdu.cloud.file.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.favorite.http.FileFavoriteController
import dk.sdu.cloud.file.favorite.processors.StorageEventProcessor
import dk.sdu.cloud.file.favorite.services.FileFavoriteHibernateDAO
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = micro.hibernateDatabase
        val kafka = micro.kafka

        // Initialize services here
        val fileFavoriteDao = FileFavoriteHibernateDAO()
        val fileFavoriteService = FileFavoriteService(db, fileFavoriteDao, client)

        // Processors
        addConsumers(StorageEventProcessor(fileFavoriteService, kafka).init())

        // Initialize server
        with(micro.server) {
            configureControllers(
                FileFavoriteController(fileFavoriteService, client)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
