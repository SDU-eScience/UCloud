package dk.sdu.cloud.app

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.JobStreams
import dk.sdu.cloud.app.api.ToolDescription
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.CallbackController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ComputationBackendService
import dk.sdu.cloud.app.services.DefaultImageGenerator
import dk.sdu.cloud.app.services.JobFileService
import dk.sdu.cloud.app.services.JobHibernateDao
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.app.services.JobVerificationService
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.developmentModeEnabled
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.stream
import dk.sdu.cloud.service.tokenValidation
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val config: Configuration,
    private val micro: Micro
) : CommonServer {
    private var initialized = false
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val toolDao = ToolHibernateDAO()
        val applicationDao = ApplicationHibernateDAO(toolDao, DefaultImageGenerator())

        val computationBackendService = ComputationBackendService(config.backends, micro.developmentModeEnabled)
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

        if (micro.commandLineArguments.contains("--scan")) {
            log.info("Scanning for expired jobs")
            runBlocking {
                log.info("Hello we are running code!")
                jobOrchestrator.removeExpiredJobs()
            }
            log.info("Done!")
            return
        }

        kStreams = buildStreams { kBuilder ->
            kBuilder.stream(JobStreams.jobStateEvents).foreach { _, event ->
                jobOrchestrator.handleStateChange(event)
            }
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    AppController(
                        db,
                        applicationDao
                    ),

                    JobController(db, jobOrchestrator, jobDao, micro.tokenValidation as TokenValidationJWT),

                    CallbackController(jobOrchestrator),

                    ToolController(
                        db,
                        toolDao
                    )
                )
            }
            log.info("HTTP server successfully configured!")
        }

        if (micro.developmentModeEnabled) {
            val listOfApps = db.withTransaction {
                applicationDao.listLatestVersion(it, null, NormalizedPaginationRequest(null, null))
            }

            if (listOfApps.itemsInTotal == 0) {
                db.withTransaction { session ->
                    val tools = File("yaml", "tools")
                    tools.listFiles().forEach {
                        try {
                            val description = yamlMapper.readValue<ToolDescription>(it)
                            toolDao.create(session, "admin@dev", description.normalize())
                        } catch (ex: Exception) {
                            log.info("Could not create tool: $it")
                            log.info(ex.stackTraceToString())
                        }
                    }

                    val apps = File("yaml", "apps")
                    apps.listFiles().forEach {
                        try {
                            val description = yamlMapper.readValue<ApplicationDescription>(it)
                            applicationDao.create(session, "admin@dev", description.normalize())
                        } catch (ex: Exception) {
                            log.info("Could not create app: $it")
                            log.info(ex.stackTraceToString())
                        }
                    }
                }
            }
        }

        log.info("Starting Application Services")
        startServices()

        initialized = true
    }
}
