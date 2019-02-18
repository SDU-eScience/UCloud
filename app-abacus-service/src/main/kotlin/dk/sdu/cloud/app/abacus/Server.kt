package dk.sdu.cloud.app.abacus

import dk.sdu.cloud.app.abacus.http.ComputeController
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobHibernateDao
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SBatchGenerator
import dk.sdu.cloud.app.abacus.services.SlurmJobTracker
import dk.sdu.cloud.app.abacus.services.SlurmPollAgent
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class Server(
    private val config: HPCConfig,
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    private lateinit var scheduledExecutor: ScheduledExecutorService
    private lateinit var slurmPollAgent: SlurmPollAgent

    override fun start() {
        log.info("Initializing core services")
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        val db = micro.hibernateDatabase
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val sshPool = SSHConnectionPool(config.ssh)
        val sBatchGenerator = SBatchGenerator()
        slurmPollAgent = SlurmPollAgent(
            ssh = sshPool,
            executor = scheduledExecutor,
            initialDelay = 0L,
            pollInterval = config.slurmPollIntervalSeconds,
            pollUnit = TimeUnit.SECONDS
        )

        val jobDao = JobHibernateDao()
        val jobFileService = JobFileService(sshPool, client, config.workingDirectory)
        val slurmScheduler =
            SlurmScheduler(
                sshPool,
                jobFileService,
                sBatchGenerator,
                slurmPollAgent,
                db,
                jobDao,
                client,
                config.reservation
            )
        val slurmTracker = SlurmJobTracker(jobFileService, sshPool, client, db, jobDao)
        slurmPollAgent.addListener(slurmTracker.listener)

        val jobTail = JobTail(sshPool, jobFileService)

        log.info("Core services initialized")

        with(micro.server) {
            configureControllers(
                ComputeController(jobFileService, slurmScheduler, jobTail)
            )
        }

        slurmPollAgent.start()
        startServices()
    }

    override fun stop() {
        super.stop()

        slurmPollAgent.stop()
        scheduledExecutor.shutdown()
    }
}
