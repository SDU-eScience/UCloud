package dk.sdu.cloud.app.orchestrator

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.orchestrator.processors.AppProcessor
import dk.sdu.cloud.app.orchestrator.rpc.*
import dk.sdu.cloud.app.orchestrator.services.JobDao
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
        @Suppress("UNCHECKED_CAST") val userClientFactory: UserClientFactory = { refreshToken ->
            RefreshingJWTAuthenticator(
                micro.client,
                refreshToken,
                micro.tokenValidation as TokenValidation<DecodedJWT>
            ).authenticateClient(OutgoingHttpCall)
        }

        val productCache = ProductCache(serviceClient)
        val paymentService = PaymentService(db, serviceClient)
        val parameterExportService = ParameterExportService(productCache)
        val jobFileService = JobFileService(userClientFactory, serviceClient, appStoreCache)
        val projectCache = ProjectCache(serviceClient)
        val providers = Providers(serviceClient)
        val providerSupportService = ProviderSupportService(providers, serviceClient)

        val jobQueryService = JobQueryService(
            db,
            projectCache,
            appStoreCache,
            productCache,
            providerSupportService,
        )

        val jobVerificationService = JobVerificationService(
            appStoreCache,
            db,
            jobQueryService,
            serviceClient,
            providers,
            productCache,
            providerSupportService,
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
                projectCache,
                micro.developmentModeEnabled,
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

        val networkDao = NetworkIPDao(productCache)
        val networkService = NetworkIPService(db, networkDao, providers, projectCache, productCache, jobOrchestrator,
            paymentService, micro.developmentModeEnabled)

        with(micro.server) {
            configureControllers(
                JobController(
                    jobQueryService,
                    jobOrchestrator,
                    providerSupportService,
                ),

                CallbackController(jobOrchestrator),

                IngressController(ingressService),

                LicenseController(licenseService),

                NetworkIPController(networkService),
            )
        }

        startServices()
    }
}
