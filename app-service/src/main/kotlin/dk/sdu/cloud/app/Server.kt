package dk.sdu.cloud.app

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.AccountingEvents
import dk.sdu.cloud.app.api.ApplicationDescription
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
import dk.sdu.cloud.app.services.OrchestrationScope
import dk.sdu.cloud.app.services.StreamFollowService
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.app.services.VncService
import dk.sdu.cloud.app.services.WebService
import dk.sdu.cloud.app.util.yamlMapper
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File

class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {
    private var initialized = false
    override val log: Logger = logger()

    override fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val streams = micro.eventStreamService
        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        OrchestrationScope.init()

        val toolDao = ToolHibernateDAO()
        val applicationDao = ApplicationHibernateDAO(toolDao)

        val computationBackendService = ComputationBackendService(config.backends, micro.developmentModeEnabled)
        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val jobDao = JobHibernateDao(applicationDao, toolDao, tokenValidation)
        val jobVerificationService =
            JobVerificationService(db, applicationDao, toolDao, tokenValidation, config.defaultBackend)
        val jobFileService = JobFileService(serviceClient)
        val streamFollowService = StreamFollowService(
            jobFileService,
            serviceClient,
            computationBackendService,
            db,
            jobDao
        )
        val vncService = VncService(computationBackendService, db, jobDao, serviceClient)
        val webService = WebService(computationBackendService, db, jobDao, serviceClient)

        val jobOrchestrator = JobOrchestrator(
            serviceClient,
            streams.createProducer(AccountingEvents.jobCompleted),
            db,
            jobVerificationService,
            computationBackendService,
            jobFileService,
            jobDao,
            config.defaultBackend
        )

        if (micro.commandLineArguments.contains("--scan")) {
            runBlocking {
                jobOrchestrator.removeExpiredJobs()
            }
            return
        }

        with(micro.server) {
            log.info("Configuring HTTP server")
            configureControllers(
                AppController(
                    db,
                    applicationDao,
                    toolDao
                ),

                JobController(
                    db,
                    jobOrchestrator,
                    jobDao,
                    streamFollowService,
                    tokenValidation,
                    serviceClient,
                    vncService,
                    webService
                ),

                CallbackController(jobOrchestrator),

                ToolController(
                    db,
                    toolDao
                )
            )
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

        log.info("Replaying lost jobs")
        jobOrchestrator.replayLostJobs()

        log.info("Starting Application Services")
        startServices()

        initialized = true
    }

    override fun stop() {
        super.stop()
        OrchestrationScope.stop()
    }
}
