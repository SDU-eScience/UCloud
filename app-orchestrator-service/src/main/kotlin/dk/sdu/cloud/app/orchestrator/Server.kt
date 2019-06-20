package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.app.orchestrator.api.AccountingEvents
import dk.sdu.cloud.app.orchestrator.rpc.CallbackController
import dk.sdu.cloud.app.orchestrator.rpc.JobController
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        OrchestrationScope.init()

        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val appStoreService = AppStoreService(serviceClient)
        val toolStoreService = ToolStoreService(serviceClient)
        val sharedMountVerificationService = SharedMountVerificationService()
        val jobVerificationService = JobVerificationService(
            appStoreService,
            toolStoreService,
            micro.tokenValidation as TokenValidationJWT,
            config.defaultBackend,
            sharedMountVerificationService
        )
        val computationBackendService = ComputationBackendService(config.backends, micro.developmentModeEnabled)
        val jobFileService = JobFileService(serviceClient)
        val jobHibernateDao = JobHibernateDao(appStoreService, toolStoreService, micro.tokenValidation)
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

        val vncService = VncService(computationBackendService, db, jobHibernateDao, serviceClient)
        val webService = WebService(computationBackendService, db, jobHibernateDao, serviceClient)



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
        jobOrchestrator.replayLostJobs()

        log.info("Starting application services")
        startServices()
    }

    override fun stop() {
        super.stop()
        OrchestrationScope.stop()
    }
}
