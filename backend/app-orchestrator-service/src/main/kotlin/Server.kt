package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.processors.AppProcessor
import dk.sdu.cloud.app.orchestrator.rpc.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.debug.DebugSystemFeature
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.file.orchestrator.service.StorageCommunication
import dk.sdu.cloud.file.orchestrator.service.StorageProviderSupport
import dk.sdu.cloud.file.orchestrator.service.StorageProviders
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import kotlinx.coroutines.runBlocking

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val streams = micro.eventStreamServiceOrNull
        val distributedLocks = DistributedLockFactory(micro)
        val appStoreCache = AppStoreCache(serviceClient)

        val projectCache = ProjectCache(DistributedStateFactory(micro), db)

        val altProviders = dk.sdu.cloud.accounting.util.Providers(serviceClient) { comms ->
            ComputeCommunication(
                JobsProvider(comms.provider.id),
                comms.client,
                comms.wsClient,
                IngressProvider(comms.provider.id),
                LicenseProvider(comms.provider.id),
                NetworkIPProvider(comms.provider.id),
                comms.provider
            )
        }

        val storageProviders = StorageProviders(serviceClient) { comms ->
            StorageCommunication(
                comms.client,
                comms.wsClient,
                comms.provider,
                FilesProvider(comms.provider.id),
                FileCollectionsProvider(comms.provider.id)
            )
        }

        val jobSupport = ProviderSupport<ComputeCommunication, Product.Compute, ComputeSupport>(
            altProviders,
            serviceClient,
            fetchSupport = { comms ->
                comms.api.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
            }
        )

        val jobOrchestrator = JobOrchestrator(
            projectCache,
            db,
            altProviders,
            jobSupport,
            serviceClient,
            appStoreCache,
        )

        val ingressSupport = ProviderSupport<ComputeCommunication, Product.Ingress, IngressSupport>(
            altProviders,
            serviceClient,
            fetchSupport = { comms ->
                comms.ingressApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
            }
        )

        val ingressService =
            IngressService(projectCache, db, altProviders, ingressSupport, serviceClient, jobOrchestrator)

        val exporter = ParameterExportService(db, ingressService)
        jobOrchestrator.exporter = exporter // TODO(Dan): Cyclic-dependency hack

        val licenseSupport = ProviderSupport<ComputeCommunication, Product.License, LicenseSupport>(
            altProviders,
            serviceClient,
            fetchSupport = { comms ->
                comms.licenseApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
            }
        )

        val licenseService =
            LicenseService(projectCache, db, altProviders, licenseSupport, serviceClient, jobOrchestrator)

        val networkIpSupport = ProviderSupport<ComputeCommunication, Product.NetworkIP, NetworkIPSupport>(
            altProviders,
            serviceClient,
            fetchSupport = { comms ->
                comms.networkApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
            }
        )
        val networkService =
            NetworkIPService(projectCache, db, altProviders, networkIpSupport, serviceClient, jobOrchestrator)

        val providerSupport = StorageProviderSupport(storageProviders, serviceClient) { comms ->
            comms.fileCollectionsApi.retrieveProducts.call(Unit, comms.client).orThrow().responses
        }

        val fileCollections = FileCollectionService(projectCache, db, storageProviders, providerSupport, serviceClient)

        val syncthingService = SyncthingService(storageProviders, serviceClient, fileCollections)

        val sshService = SshKeyService(db, jobOrchestrator, altProviders)

        val jobMonitoring = JobMonitoringService(
            micro.feature(DebugSystemFeature).system,
            micro.backgroundScope,
            distributedLocks,
            db,
            jobOrchestrator,
            altProviders,
            fileCollections,
            ingressService,
            networkService,
            licenseService
        )

        runBlocking { jobMonitoring.initialize(!micro.developmentModeEnabled) }

        if (streams != null) AppProcessor(streams, jobOrchestrator, appStoreCache).init()

        configureControllers(
            JobController(db, jobOrchestrator, micro),
            ingressService.asController(),
            licenseService.asController(),
            NetworkIPController(networkService),
            SyncthingController(syncthingService),
            SshKeyController(sshService),
        )

        startServices()
    }
}
