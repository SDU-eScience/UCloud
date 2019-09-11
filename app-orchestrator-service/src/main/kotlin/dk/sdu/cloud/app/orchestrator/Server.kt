package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.app.orchestrator.api.AccountingEvents
import dk.sdu.cloud.app.orchestrator.rpc.CallbackController
import dk.sdu.cloud.app.orchestrator.rpc.JobController
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
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
import io.ktor.routing.routing

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        OrchestrationScope.init()

        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val serviceClientWS = micro.authenticator.authenticateClient(OutgoingWSCall)
        val appStoreService = AppStoreService(serviceClient)
        val toolStoreService = ToolStoreService(serviceClient)
        val jobHibernateDao = JobHibernateDao(appStoreService, toolStoreService)
        val sharedMountVerificationService = SharedMountVerificationService()
        val computationBackendService = ComputationBackendService(config.backends, micro.developmentModeEnabled)
        val userClientFactory: (String?, String?) -> AuthenticatedClient = { accessToken, refreshToken ->
            when {
                accessToken != null -> {
                    serviceClient.withoutAuthentication().bearerAuth(accessToken)
                }

                refreshToken != null -> {
                    RefreshingJWTAuthenticator(
                        serviceClient.client,
                        refreshToken,
                        micro.tokenValidation as TokenValidationJWT
                    ).authenticateClient(OutgoingHttpCall)
                }

                else -> {
                    throw IllegalStateException("No token found!")
                }
            }
        }

        val parameterExportService = ParameterExportService()
        val jobFileService = JobFileService(serviceClient, userClientFactory, parameterExportService)

        val vncService = VncService(computationBackendService, db, jobHibernateDao, serviceClient)
        val webService = WebService(computationBackendService, db, jobHibernateDao, serviceClient)

        val jobVerificationService = JobVerificationService(
            appStoreService,
            toolStoreService,
            config.defaultBackend,
            sharedMountVerificationService,
            db,
            jobHibernateDao,
            config.machines
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
                serviceClientWS,
                computationBackendService,
                db,
                jobHibernateDao
            )

        val jobQueryService = JobQueryService(db, jobHibernateDao, jobFileService)

        with(micro.server) {
            configureControllers(
                JobController(
                    jobQueryService,
                    jobOrchestrator,
                    streamFollowService,
                    userClientFactory,
                    serviceClient,
                    vncService,
                    webService,
                    config.machines
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
