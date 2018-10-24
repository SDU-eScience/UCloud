package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationDescriptions
import dk.sdu.cloud.app.api.JobStreams
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.ToolDescription
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.CallbackController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ComputationBackendService
import dk.sdu.cloud.app.services.JobFileService
import dk.sdu.cloud.app.services.JobHibernateDao
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.app.services.JobVerificationService
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
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
    private val config: Configuration,
    private val developmentModeEnabled: Boolean
) : CommonServer {
    private var initialized = false
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val toolDao = ToolHibernateDAO()
        val applicationDao = ApplicationHibernateDAO(toolDao)

        val computationBackendService = ComputationBackendService(config.backends)
        val jobDao = JobHibernateDao(applicationDao)
        val jobVerificationService = JobVerificationService(db, applicationDao)
        val jobFileService = JobFileService(cloud)

        val jobOrchestrator = JobOrchestrator(
            cloud,
            kafka.producer.forStream(JobStreams.jobStateEvents),
            kafka.producer.forStream(AccountingEvents.jobCompleted),
            db,
            jobVerificationService,
            computationBackendService,
            jobFileService,
            jobDao
        )

        if (developmentModeEnabled) {
            val listOfApps = db.withTransaction {
                applicationDao.listLatestVersion(it, null, NormalizedPaginationRequest(null, null))
            }

            if (listOfApps.itemsInTotal == 0) {
                db.withTransaction {
                    toolDao.create(
                        it,
                        "admin@dev",
                        ToolDescription.V1(
                            name = "tool",
                            version = "1.0.0",
                            title = "Tool",
                            container = "tool",
                            backend = ToolBackend.UDOCKER,
                            authors = listOf("Dan Thrane")
                        ).normalize()
                    )

                    applicationDao.create(
                        it,
                        "admin@dev",
                        ApplicationDescription.V1(
                            name = "app",
                            version = "1.0.0",
                            tool = NameAndVersion("tool", "1.0.0"),
                            authors = listOf("Dan Thrane"),
                            title = "App",
                            description = "Description",
                            invocation = listOf("foo")
                        ).normalize()
                    )

                    log.info("Created development 'tool@1.0.0' and 'app@1.0.0'")
                }
            }
        }

        kStreams = buildStreams { kBuilder ->
            kBuilder.stream(JobStreams.jobStateEvents).foreach { _, event ->
                jobOrchestrator.handleStateChange(event)
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

                    JobController(db, jobOrchestrator, jobDao),

                    CallbackController(jobOrchestrator),

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
