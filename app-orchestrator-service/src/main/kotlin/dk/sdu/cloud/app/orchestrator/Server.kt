package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.app.orchestrator.api.AccountingEvents
import dk.sdu.cloud.app.orchestrator.rpc.CallbackController
import dk.sdu.cloud.app.orchestrator.rpc.JobController
import dk.sdu.cloud.app.orchestrator.services.AppStoreService
import dk.sdu.cloud.app.orchestrator.services.ComputationBackendService
import dk.sdu.cloud.app.orchestrator.services.JobFileService
import dk.sdu.cloud.app.orchestrator.services.JobHibernateDao
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.JobVerificationService
import dk.sdu.cloud.app.orchestrator.services.OrchestrationScope
import dk.sdu.cloud.app.orchestrator.services.SharedMountVerificationService
import dk.sdu.cloud.app.orchestrator.services.StreamFollowService
import dk.sdu.cloud.app.orchestrator.services.ToolStoreService
import dk.sdu.cloud.app.orchestrator.services.VncService
import dk.sdu.cloud.app.orchestrator.services.WebService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        OrchestrationScope.init()

        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val appStoreService = AppStoreService(serviceClient)
        val toolStoreService = ToolStoreService(serviceClient)
        val jobHibernateDao = JobHibernateDao(appStoreService, toolStoreService, micro.tokenValidation)
        val sharedMountVerificationService = SharedMountVerificationService()
        val computationBackendService = ComputationBackendService(config.backends, micro.developmentModeEnabled)
        val jobFileService = JobFileService(serviceClient)

        val vncService = VncService(computationBackendService, db, jobHibernateDao, serviceClient)
        val webService = WebService(computationBackendService, db, jobHibernateDao, serviceClient)

        val jobVerificationService = JobVerificationService(
            appStoreService,
            toolStoreService,
            sharedMountVerificationService,
            jobHibernateDao,
            db,
            micro.tokenValidation as TokenValidationJWT,
            config.defaultBackend
        )

        val jobOrchestrator =
            JobOrchestrator(
                serviceClient,
                micro.eventStreamService.createProducer(AccountingEvents.jobCompleted),
                db,
                jobVerificationService,
                computationBackendService,
                jobFileService,
                jobHibernateDao,
                config.defaultBackend
            )

        val streamFollowService =
            StreamFollowService(
                jobFileService,
                serviceClient,
                computationBackendService,
                db,
                jobHibernateDao
            )

        with(micro.server) {
            configureControllers(
                JobController(
                    db,
                    jobOrchestrator,
                    jobHibernateDao,
                    streamFollowService,
                    micro.tokenValidation as TokenValidationJWT,
                    serviceClient,
                    vncService,
                    webService
                ),
                CallbackController(jobOrchestrator)
            )
        }


        log.info("Replaying lost jobs")
        @Suppress("TooGenericExceptionCaught")
        try {
            jobOrchestrator.replayLostJobs()
        } catch (ex: Throwable) {
            log.warn("Caught exception while replaying lost jobs. These are ignored!")
            log.warn(ex.stackTraceToString())
            log.warn("Caught exception while replaying lost jobs. These are ignored!")
        }

        log.info("Starting application services")
        startServices()
    }

    override fun stop() {
        super.stop()
        OrchestrationScope.stop()
    }
}
