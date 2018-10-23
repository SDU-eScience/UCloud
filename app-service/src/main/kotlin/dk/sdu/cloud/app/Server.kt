package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.HPCStreams
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val instance: ServiceInstance
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
        val computationBackendService = ComputationBackendService()

        val jobExecutionService = JobExecutionService(
            cloud,
            kafka.producer.forStream(HPCStreams.jobStateEvents),
            kafka.producer.forStream(AccountingEvents.jobCompleted),
            db,
            jobVerificationService,
            computationBackendService,
            jobDao
        )

        kStreams = buildStreams { kBuilder ->
            kBuilder.stream(HPCStreams.jobStateEvents).foreach { _, event ->
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
