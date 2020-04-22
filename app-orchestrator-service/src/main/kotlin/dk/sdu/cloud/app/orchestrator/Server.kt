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
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = micro.hibernateDatabase
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val serviceClientWS = micro.authenticator.authenticateClient(OutgoingWSCall)
        val appStoreService = AppStoreService(serviceClient)
        val toolStoreService = ToolStoreService(serviceClient)
        val jobHibernateDao = JobHibernateDao(appStoreService, toolStoreService)
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
        val jobFileService = JobFileService(userClientFactory, parameterExportService, serviceClient)

        val vncService = VncService(computationBackendService, db, jobHibernateDao, serviceClient)
        val webService = WebService(computationBackendService, db, jobHibernateDao, serviceClient)

        val jobQueryService = JobQueryService(db, jobHibernateDao, jobFileService, ProjectCache(serviceClient))

        val jobVerificationService = JobVerificationService(
            appStoreService,
            toolStoreService,
            config.defaultBackend,
            db,
            jobHibernateDao,
            serviceClient,
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
                jobQueryService,
                config.defaultBackend,
                micro.backgroundScope
            )

        val streamFollowService =
            StreamFollowService(
                jobFileService,
                serviceClient,
                serviceClientWS,
                computationBackendService,
                jobQueryService,
                micro.backgroundScope
            )


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
                    config.machines,
                    config.gpuWhitelist
                ),
                CallbackController(jobOrchestrator)
            )
        }

        log.info("Starting application services")
        startServices()
    }
}
