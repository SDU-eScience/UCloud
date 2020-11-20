package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.rpc.MaintenanceController
import dk.sdu.cloud.app.kubernetes.rpc.ReloadController
import dk.sdu.cloud.app.kubernetes.rpc.ShellController
import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.app.kubernetes.services.proxy.ApplicationProxyService
import dk.sdu.cloud.app.kubernetes.services.proxy.AuthenticationService
import dk.sdu.cloud.app.kubernetes.services.proxy.EnvoyConfigurationService
import dk.sdu.cloud.app.kubernetes.services.proxy.TunnelManager
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.RedisBroadcastingStream
import dk.sdu.cloud.service.RedisDistributedStateFactory
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.k8.KubernetesClient
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import kotlinx.coroutines.runBlocking
import java.io.File

class Server(
    override val micro: Micro,
    private val configuration: Configuration,
    private val cephConfig: CephConfiguration
) : CommonServer {
    override val log = logger()
    lateinit var tunnelManager: TunnelManager
    lateinit var vncService: VncService
    lateinit var webService: WebService

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val broadcastingStream = RedisBroadcastingStream(micro.redisConnectionManager)
        val distributedLocks = DistributedLockBestEffortFactory(micro)
        val jobCache = VerifiedJobCache(serviceClient)
        val nameAllocator = NameAllocator()
        val db = AsyncDBSessionFactory(micro.databaseConfig)

        val k8sClient = KubernetesClient()

        val k8Dependencies = K8Dependencies(
            k8sClient,
            micro.backgroundScope,
            serviceClient,
            nameAllocator,
            DockerImageSizeQuery()
        )

        val logService = K8LogService(k8Dependencies)
        val maintenance = MaintenanceService(db, k8Dependencies)
        val resourceCache = ResourceCache(serviceClient)

        val jobManagement = JobManagement(
            k8Dependencies,
            distributedLocks,
            logService,
            jobCache,
            maintenance,
            resourceCache,
            // NOTE(Dan): The master lock can be annoying to deal with during development (when we only have one
            // instance) In that case we can disable it via configuration. Note that this config will only be used if
            // we are in development mode.
            configuration.disableMasterElection && micro.developmentModeEnabled,
            configuration.fullScanFrequency
        ).apply {
            register(TaskPlugin(configuration.toleration))
            register(ParameterPlugin)
            register(FileMountPlugin(cephConfig))
            register(MultiNodePlugin)
            register(SharedMemoryPlugin)
            register(ExpiryPlugin)
            register(AccountingPlugin)
            register(MiscellaneousPlugin)
            register(FairSharePlugin)
            if (micro.developmentModeEnabled) register(MinikubePlugin)
            register(ConnectToJobPlugin)
            register(ProxyPlugin(broadcastingStream))

            // NOTE(Dan): Kata Containers are not currently enabled due to various limitations in Kata containers
            // related to our infrastructure setup
            // register(KataContainerPlugin())
        }

        val envoyConfigurationService = EnvoyConfigurationService(
            File("./envoy/rds.yaml"),
            File("./envoy/clusters.yaml")
        )

        run {
            val port = micro.featureOrNull(ServiceDiscoveryOverrides)?.get(micro.serviceDescription.name)?.port
                ?: 8080
            val renderedConfig =
                Server::class.java.classLoader.getResourceAsStream("config_template.yaml")
                    ?.bufferedReader()
                    ?.readText()
                    ?.replace("\$SERVICE_PORT", port.toString())
                    ?.replace("\$PWD", File("./envoy").absoluteFile.normalize().absolutePath)
                    ?: throw IllegalStateException("Could not find config_template.yml in classpath")

            val configFile = File("./envoy/config.yaml")
            configFile.writeText(renderedConfig)
            log.info("Wrote configuration at ${configFile.absolutePath}")
        }

        val authenticationService = AuthenticationService(serviceClient, micro.tokenValidation)
        tunnelManager = TunnelManager(k8Dependencies)
        tunnelManager.install()

        val applicationProxyService = ApplicationProxyService(
            envoyConfigurationService,
            prefix = configuration.prefix,
            domain = configuration.domain,
            jobCache = jobCache,
            tunnelManager = tunnelManager,
            broadcastingStream = broadcastingStream,
            resources = resourceCache
        )

        vncService = VncService(tunnelManager)
        webService = WebService(
            authenticationService = authenticationService,
            performAuthentication = configuration.performAuthentication,
            cookieName = configuration.cookieName,
            prefix = configuration.prefix,
            domain = configuration.domain,
            broadcastingStream = broadcastingStream,
            jobCache = jobCache,
            k8 = k8Dependencies,
            devMode = micro.developmentModeEnabled
        )

        with(micro.server) {
            configureControllers(
                AppKubernetesController(
                    jobManagement,
                    logService,
                    vncService,
                    webService,
                    broadcastingStream
                ),
                ReloadController(k8Dependencies),
                MaintenanceController(maintenance),
                ShellController(k8Dependencies)
            )
        }

        runBlocking {
            applicationProxyService.initializeListener()
            jobManagement.initializeListeners()
        }

        startServices(wait = false)
    }

    override fun onKtorReady() {
        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!

        ktorEngine.application.routing {
            vncService.install(this)
            webService.install(this)
        }
    }

    override fun stop() {
        super.stop()
        tunnelManager.shutdown()
    }
}
