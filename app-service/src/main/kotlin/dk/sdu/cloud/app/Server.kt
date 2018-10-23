package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.JobStreams
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ComputationBackendService
import dk.sdu.cloud.app.services.JobExecutionService
import dk.sdu.cloud.app.services.JobHibernateDao
import dk.sdu.cloud.app.services.JobVerificationService
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.stream
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val instance: ServiceInstance,
    private val config: Configuration
) : CommonServer {
    private var initialized = false
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val toolDao = ToolHibernateDAO()
        val applicationDao = ApplicationHibernateDAO(toolDao)
        val jobDao = JobHibernateDao(applicationDao)
        val jobVerificationService = JobVerificationService(db, applicationDao)
        val computationBackendService = ComputationBackendService(config.backends)

        val jobExecutionService = JobExecutionService(
            cloud,
            kafka.producer.forStream(JobStreams.jobStateEvents),
            kafka.producer.forStream(AccountingEvents.jobCompleted),
            db,
            jobVerificationService,
            computationBackendService,
            jobDao
        )

        kStreams = buildStreams { kBuilder ->
            kBuilder.stream(JobStreams.jobStateEvents).foreach { _, event ->
                jobExecutionService.handleStateChange(event)
            }
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance)

            routing {
                configureControllers(
                    AppController(
                        db,
                        applicationDao
                    ),

                    JobController(jobExecutionService),

                    ToolController(
                        db,
                        toolDao
                    )
                )
            }
            log.info("HTTP server successfully configured!")
        }
        log.info("Starting Application Services")

        startServices()

        initialized = true
    }
}
