package dk.sdu.cloud.app.orchestrator

import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.ProviderCommunicationsV2
import dk.sdu.cloud.app.orchestrator.api.*
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
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.writer
import kotlinx.coroutines.isActive
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

    lateinit var providers: ProviderCommunicationsV2
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

    lateinit var statistics: StatisticsService
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
            idCards = IdCardService(db, backgroundScope, serviceClient)

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
            providers = ProviderCommunicationsV2(micro.backgroundScope, serviceClient, productCache)
            fileCollections = FileCollectionService(projectCache, db, storageProviders, storageSupport, serviceClient)

            jobs = JobResourceService(serviceClient)

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
            statistics = StatisticsService()
            runBlocking { jobMonitoring.initialize(!micro.developmentModeEnabled) }

            micro.serverProvider(33301) {
                routing {
                    get("/") {
                        val providerId = call.request.queryParameters["providerId"]
                        val dry = call.request.queryParameters["dry"]

                        if (providerId == null || dry == null) {
                            call.respondText("Bad invocation", status = HttpStatusCode.BadRequest)
                        } else {
                            call.respondTextWriter(ContentType.Text.Plain, status = HttpStatusCode.OK) {
                                val channel =
                                    ProviderMigration(db).dumpProviderData(providerId, dryRun = dry != "false")
                                try {
                                    for (message in channel) {
                                        write(message + "\n")
                                        flush()
                                    }
                                } catch (ex: Throwable) {
                                    write("ERROR: ${ex.toReadableStacktrace()}")
                                    flush()
                                } finally {
                                    close()
                                }
                            }
                        }
                    }
                }
            }.start(wait = false)

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
