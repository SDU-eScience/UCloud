package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.ComputeProvider
import dk.sdu.cloud.app.orchestrator.api.ComputeProviderManifest
import dk.sdu.cloud.app.orchestrator.api.LicenseControl
import dk.sdu.cloud.app.orchestrator.api.ProviderManifest
import dk.sdu.cloud.app.orchestrator.processors.AppProcessor
import dk.sdu.cloud.app.orchestrator.services.JobDao
import dk.sdu.cloud.app.orchestrator.rpc.CallbackController
import dk.sdu.cloud.app.orchestrator.rpc.IngressController
import dk.sdu.cloud.app.orchestrator.rpc.JobController
import dk.sdu.cloud.app.orchestrator.rpc.LicenseController
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import kotlinx.coroutines.runBlocking

typealias UserClientFactory = (refreshToken: String) -> AuthenticatedClient

class Server(override val micro: Micro, val config: Configuration) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val serviceClientWS = micro.authenticator.authenticateClient(OutgoingWSCall)
        val streams = micro.eventStreamService
        val distributedLocks = DistributedLockBestEffortFactory(micro)
        val appStoreCache = AppStoreCache(serviceClient)
        val jobDao = JobDao()
        val userClientFactory: UserClientFactory = { refreshToken ->
            RefreshingJWTAuthenticator(
                micro.client,
                refreshToken,
                micro.tokenValidation as TokenValidationJWT
            ).authenticateClient(OutgoingHttpCall)
        }

        val productCache = ProductCache(serviceClient)
        val paymentService = PaymentService(db, serviceClient)
        val parameterExportService = ParameterExportService()
        val jobFileService = JobFileService(userClientFactory, serviceClient, appStoreCache)
        val projectCache = ProjectCache(serviceClient)

        val jobQueryService = JobQueryService(
            db,
            projectCache,
            appStoreCache,
            productCache,
        )

        // TODO Providers
        val providers = Providers(
            micro.developmentModeEnabled,
            serviceClient,
            serviceClientWS,
            ComputeProviderManifest(
                ComputeProvider(UCLOUD_PROVIDER, "localhost", false, 8080),
                ProviderManifest().apply {
                    with(features) {
                        with(compute) {
                            with(docker) {
                                enabled = true
                                web = true
                                vnc = true
                                batch = true
                                logs = true
                                peers = true
                                terminal = true
                            }
                        }
                    }
                }
            )
        )

        val jobVerificationService = JobVerificationService(
            appStoreCache,
            db,
            jobQueryService,
            serviceClient,
            providers,
            productCache
        )

        val jobOrchestrator =
            JobOrchestrator(
                serviceClient,
                db,
                jobVerificationService,
                jobFileService,
                jobDao,
                jobQueryService,
                paymentService,
                providers,
                userClientFactory,
                parameterExportService,
                projectCache
            )

        val jobMonitoring = JobMonitoringService(
            db,
            micro.backgroundScope,
            distributedLocks,
            jobVerificationService,
            jobOrchestrator,
            providers,
            jobQueryService,
        )

        AppProcessor(
            streams,
            jobOrchestrator,
            appStoreCache
        ).init()

        runBlocking { jobMonitoring.initialize() }

        val ingressDao = IngressDao(productCache)
        val ingressService = IngressService(db, ingressDao, providers, projectCache, productCache,
            jobOrchestrator, paymentService)

        val licenseDao = LicenseDao(productCache)
        val licenseService = LicenseService(db, licenseDao, providers, projectCache, productCache,
            jobOrchestrator, paymentService)

        with(micro.server) {
            configureControllers(
                JobController(
                    jobQueryService,
                    jobOrchestrator,
                ),

                CallbackController(jobOrchestrator),

                IngressController(ingressService),

                LicenseController(licenseService),
            )
        }

        startServices()
    }
}
