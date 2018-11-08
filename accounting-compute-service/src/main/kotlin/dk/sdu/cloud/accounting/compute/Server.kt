package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.http.ComputeAccountingController
import dk.sdu.cloud.accounting.compute.http.ComputeTimeController
import dk.sdu.cloud.accounting.compute.http.JobsStartedController
import dk.sdu.cloud.accounting.compute.processor.JobCompletedProcessor
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.stackTraceToString
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
        val completedJobsService = CompletedJobsService(db, completedJobsDao)

        // Processors
        JobCompletedProcessor(kafka, completedJobsService).init().let { batch ->
            batch.forEach { it.installExceptionHandler() }
            processors.addAll(batch)
        }

        // HTTP
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    JobsStartedController(),
                    ComputeTimeController(completedJobsService),
                    ComputeAccountingController(completedJobsService)
                )
            }
        }

        log.info("Server is ready!")

        startServices()
    }

    private fun EventConsumer<*>.installExceptionHandler() {
        onExceptionCaught { ex ->
            log.warn("Caught fatal exception in consumer!")
            log.warn(ex.stackTraceToString())
            stop()
        }
    }

    override fun stop() {
        super.stop()
    }
}
