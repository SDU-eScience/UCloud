package dk.sdu.cloud.app.orchestrator

import app.orchestrator.rpc.PublicLinkController
import dk.sdu.cloud.app.orchestrator.processors.AppProcessor
import dk.sdu.cloud.app.orchestrator.services.JobDao
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
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val serviceClientWS = micro.authenticator.authenticateClient(OutgoingWSCall)
        val streams = micro.eventStreamService
        val applicationService = ApplicationService(serviceClient)
        val jobHibernateDao = JobDao()
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

        val machineCache = MachineTypeCache(serviceClient)
        val paymentService = PaymentService(db, serviceClient)
        val parameterExportService = ParameterExportService()
        val jobFileService = JobFileService(userClientFactory, parameterExportService, serviceClient)
        val publicLinks = PublicLinkService()

        val jobQueryService = JobQueryService(
            db,
            jobFileService,
            ProjectCache(serviceClient),
            applicationService,
            publicLinks
        )

        val vncService = VncService(computationBackendService, db, jobQueryService, serviceClient)
        val webService = WebService(computationBackendService, db, jobQueryService, serviceClient)

        val jobVerificationService = JobVerificationService(
            applicationService,
            config.defaultBackend,
            db,
            jobQueryService,
            serviceClient,
            machineCache
        )

        val jobOrchestrator =
            JobOrchestrator(
                serviceClient,
                db,
                jobVerificationService,
                computationBackendService,
                jobFileService,
                jobHibernateDao,
                jobQueryService,
                config.defaultBackend,
                micro.backgroundScope,
                paymentService
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

        AppProcessor(
            streams,
            jobOrchestrator,
            applicationService
        ).init()

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
                    machineCache
                ),

                CallbackController(jobOrchestrator),

                PublicLinkController(
                    db,
                    jobQueryService,
                    publicLinks
                )
            )
        }

        log.info("Starting application services")
        startServices()
    }
}
