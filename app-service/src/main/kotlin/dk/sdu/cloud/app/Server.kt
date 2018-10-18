package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.HPCStreams
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.JobExecutionService
import dk.sdu.cloud.app.services.JobHibernateDAO
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.services.SBatchGenerator
import dk.sdu.cloud.app.services.SlurmPollAgent
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val POLL_INTERVAL = 15L


class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val config: HPCConfig,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val instance: ServiceInstance
) : CommonServer {
    private var initialized = false
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams
    private lateinit var scheduledExecutor: ScheduledExecutorService
    private lateinit var slurmPollAgent: SlurmPollAgent

    override fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        log.info("Init Core Services")
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        log.info("Init Application Services")
        val sshPool = SSHConnectionPool(config.ssh)
        val sbatchGenerator = SBatchGenerator()
        slurmPollAgent = SlurmPollAgent(sshPool, scheduledExecutor, 0L, POLL_INTERVAL, TimeUnit.SECONDS)

        val toolDao = ToolHibernateDAO()
        val applicationDao = ApplicationHibernateDAO(toolDao)
        val jobDao = JobHibernateDAO(applicationDao)
        val jobExecutionService = JobExecutionService(
            cloud,
            kafka.producer.forStream(HPCStreams.appEvents),
            kafka.producer.forStream(AccountingEvents.jobCompleted),
            sbatchGenerator,
            db,
            jobDao,
            applicationDao,
            slurmPollAgent,
            sshPool,
            config.ssh.user
        )
        val jobService = JobService(db, jobDao, sshPool, jobExecutionService)

        kStreams = buildStreams { kBuilder ->
            kBuilder.stream(HPCStreams.appEvents).foreach { _, event -> jobExecutionService.handleAppEvent(event) }
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

                    JobController(
                        jobService
                    ),

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
        slurmPollAgent.start()
        jobExecutionService.initialize()

        initialized = true
    }

    override fun stop() {
        super.stop() // stops kStreams and httpServer
        slurmPollAgent.stop()
        scheduledExecutor.shutdown()
    }
}
