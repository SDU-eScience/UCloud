package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.util.PaymentService
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.processors.AppProcessor
import dk.sdu.cloud.app.orchestrator.rpc.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.DebugSystemFeature
import dk.sdu.cloud.events.EventStreamService
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

object AppOrchestratorServices {
    lateinit var micro: Micro
    lateinit var debug: DebugSystem
    lateinit var db: AsyncDBSessionFactory
    lateinit var backgroundScope: BackgroundScope
    lateinit var serviceClient: AuthenticatedClient
    var eventStreams: EventStreamService? = null
    lateinit var distributedLocks: DistributedLockFactory
    lateinit var distributedState: DistributedStateFactory

    lateinit var projectCache: ProjectCache
    lateinit var productCache: ProductCache
    lateinit var appCache: ApplicationCache

    lateinit var providers: ProviderCommunications
    lateinit var altProviders: dk.sdu.cloud.accounting.util.Providers<ComputeCommunication>
    lateinit var storageProviders: StorageProviders

    lateinit var ingressSupport: ProviderSupport<ComputeCommunication, Product.Ingress, IngressSupport>
    lateinit var licenseSupport: ProviderSupport<ComputeCommunication, Product.License, LicenseSupport>
    lateinit var networkIpSupport: ProviderSupport<ComputeCommunication, Product.NetworkIP, NetworkIPSupport>
    lateinit var storageSupport: StorageProviderSupport

    lateinit var payment: PaymentService
    lateinit var exporter: ParameterExportService

    lateinit var idCards: IIdCardService

    lateinit var jobs: JobResourceService

    lateinit var fileCollections: FileCollectionService
    lateinit var publicIps: NetworkIPService
    lateinit var publicLinks: IngressService
    lateinit var licenses: LicenseService
    lateinit var syncthing: SyncthingService
    lateinit var sshKeys: SshKeyService

    lateinit var jobMonitoring: JobMonitoringService
}

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        with(AppOrchestratorServices) Services@{
            this@Services.micro = this@Server.micro
            debug = micro.feature(DebugSystemFeature).system
            db = AsyncDBSessionFactory(micro)
            serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
            eventStreams = micro.eventStreamServiceOrNull
            distributedLocks = DistributedLockFactory(micro)
            backgroundScope = micro.backgroundScope
            distributedState = DistributedStateFactory(micro)

            projectCache = ProjectCache(distributedState, db)
            productCache = ProductCache(db)
            appCache = ApplicationCache(db)

            altProviders = dk.sdu.cloud.accounting.util.Providers(serviceClient) { comms ->
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

            storageProviders = StorageProviders(serviceClient) { comms ->
                StorageCommunication(
                    comms.client,
                    comms.wsClient,
                    comms.provider,
                    FilesProvider(comms.provider.id),
                    FileCollectionsProvider(comms.provider.id)
                )
            }

            storageSupport = StorageProviderSupport(storageProviders, serviceClient) { comms ->
                comms.fileCollectionsApi.retrieveProducts.call(Unit, comms.client).orThrow().responses
            }

            payment = PaymentService(db, serviceClient)
            providers = ProviderCommunications(micro.backgroundScope, serviceClient, productCache)
            fileCollections = FileCollectionService(projectCache, db, storageProviders, storageSupport, serviceClient)

            jobs = JobResourceService()

            ingressSupport = ProviderSupport(
                altProviders,
                serviceClient,
                fetchSupport = { comms ->
                    comms.ingressApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
                }
            )

            publicLinks = IngressService(projectCache, db, altProviders, ingressSupport, serviceClient, jobs)

            licenseSupport = ProviderSupport(
                altProviders,
                serviceClient,
                fetchSupport = { comms ->
                    comms.licenseApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
                }
            )

            licenses = LicenseService(projectCache, db, altProviders, licenseSupport, serviceClient, jobs)

            networkIpSupport = ProviderSupport(
                altProviders,
                serviceClient,
                fetchSupport = { comms ->
                    comms.networkApi.retrieveProducts.call(Unit, comms.client).orThrow().responses.map { it }
                }
            )
            publicIps = NetworkIPService(projectCache, db, altProviders, networkIpSupport, serviceClient, jobs)

            syncthing = SyncthingService()
            sshKeys = SshKeyService()
            exporter = ParameterExportService()
            jobMonitoring = JobMonitoringService()
            runBlocking { jobMonitoring.initialize(!micro.developmentModeEnabled) }

            eventStreams?.let { streams -> AppProcessor(streams, appCache).init() }

            configureControllers(
                JobController(jobs, micro),
                publicLinks.asController(),
                licenses.asController(),
                NetworkIPController(publicIps),
                SyncthingController(syncthing),
                SshKeyController(sshKeys),
            )
        }

        startServices()
    }
}
