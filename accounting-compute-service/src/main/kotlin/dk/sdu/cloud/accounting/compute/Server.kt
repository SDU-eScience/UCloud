package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.http.ComputeAccountingController
import dk.sdu.cloud.accounting.compute.http.ComputeTimeController
import dk.sdu.cloud.accounting.compute.processor.JobCompletedProcessor
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val micro: Micro
) : CommonServer {
    override val log = logger()
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null

    private val processors = ArrayList<EventConsumer<*>>()

    override fun start() {
        // Services
        val completedJobsDao = CompletedJobsHibernateDao()
        val completedJobsService = CompletedJobsService(db, completedJobsDao, micro.authenticatedCloud)

        // Processors
        JobCompletedProcessor(kafka, completedJobsService, micro.authenticatedCloud).init().let { batch ->
            batch.forEach { it.installShutdownHandler(this) }
            processors.addAll(batch)
        }

        // HTTP
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    ComputeTimeController(completedJobsService),
                    ComputeAccountingController(completedJobsService)
                )
            }
        }

        log.info("Server is ready!")

        startServices()
    }

    override fun stop() {
        super.stop()
        processors.forEach { it.close() }
    }
}
