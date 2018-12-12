package dk.sdu.cloud.accounting.storage

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.accounting.storage.http.StorageAccountingController
import dk.sdu.cloud.accounting.storage.http.StorageUsedController
import dk.sdu.cloud.accounting.storage.services.StorageAccountingHibernateDao
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val micro: Micro,
    private val config: Configuration
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

    override fun start() {
        // Initialize services here
        val storageAccountingService =
            StorageAccountingService(
                cloud,
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
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    StorageAccountingController(storageAccountingService),
                    StorageUsedController(storageAccountingService)
                )
            }
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
