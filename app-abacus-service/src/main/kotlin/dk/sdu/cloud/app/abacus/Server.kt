package dk.sdu.cloud.app.abacus

import dk.sdu.cloud.app.abacus.http.ComputeController
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.SBatchGenerator
import dk.sdu.cloud.app.abacus.services.SlurmJobTracker
import dk.sdu.cloud.app.abacus.services.SlurmPollAgent
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val config: HPCConfig,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val instance: ServiceInstance
) : CommonServer {
    override val log = logger()

    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null

    private lateinit var scheduledExecutor: ScheduledExecutorService
    private lateinit var slurmPollAgent: SlurmPollAgent
    private lateinit var slurmTracker: SlurmJobTracker

    override fun start() {
        log.info("Initializing core services")
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        val sshPool = SSHConnectionPool(config.ssh)
        val sBatchGenerator = SBatchGenerator()
        slurmPollAgent = SlurmPollAgent(
            ssh = sshPool,
            executor = scheduledExecutor,
            initialDelay = 0L,
            pollInterval = config.slurmPollIntervalSeconds,
            pollUnit = TimeUnit.SECONDS
        )

        val jobFileService = JobFileService(sshPool, cloud, config.workingDirectory)
        val slurmService = SlurmScheduler(sshPool, jobFileService, sBatchGenerator, slurmPollAgent)
        slurmTracker = SlurmJobTracker(slurmPollAgent, jobFileService, sshPool, cloud).also { it.init() }

        log.info("Core services initialized")

        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)

            routing {
                configureControllers(
                    ComputeController(jobFileService, slurmService)
                )
            }
        }

        startServices()
        slurmPollAgent.start()
    }

    override fun stop() {
        super.stop()

        slurmPollAgent.stop()
        scheduledExecutor.shutdown()
        slurmTracker.stop()
    }
}
