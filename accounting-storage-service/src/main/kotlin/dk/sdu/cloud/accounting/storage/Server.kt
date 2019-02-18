package dk.sdu.cloud.accounting.storage

import dk.sdu.cloud.accounting.storage.http.StorageAccountingController
import dk.sdu.cloud.accounting.storage.http.StorageUsedController
import dk.sdu.cloud.accounting.storage.services.StorageAccountingHibernateDao
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking

class Server(
    private val config: Configuration,
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
        // Initialize services here
        val storageAccountingService =
            StorageAccountingService(
                client,
                micro.hibernateDatabase,
                StorageAccountingHibernateDao(),
                config
            )

        if (micro.commandLineArguments.contains("--scan")) {
            log.info("Running scan instead of server")
            runBlocking {
                storageAccountingService.collectCurrentStorageUsage()
            }
            log.info("Scan complete")
            return
        }

        // Initialize consumers here:
        // addConsumers(...)

        // Initialize server
        with(micro.server) {
            configureControllers(
                StorageAccountingController(storageAccountingService, client),
                StorageUsedController(storageAccountingService, client)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
