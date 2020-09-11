package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.rpc.ReloadController
import dk.sdu.cloud.app.kubernetes.services.ApplicationProxyService
import dk.sdu.cloud.app.kubernetes.services.AuthenticationService
import dk.sdu.cloud.app.kubernetes.services.EnvoyConfigurationService
import dk.sdu.cloud.app.kubernetes.services.HostAliasesService
import dk.sdu.cloud.app.kubernetes.services.K8Dependencies
import dk.sdu.cloud.app.kubernetes.services.K8JobCreationService
import dk.sdu.cloud.app.kubernetes.services.K8JobMonitoringService
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.K8NameAllocator
import dk.sdu.cloud.app.kubernetes.services.NetworkPolicyService
import dk.sdu.cloud.app.kubernetes.services.ReloadableKubernetesClient
import dk.sdu.cloud.app.kubernetes.services.TunnelManager
import dk.sdu.cloud.app.kubernetes.services.VerifiedJobCache
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.app.kubernetes.services.WorkspaceService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.micro.featureOrNull
import dk.sdu.cloud.micro.redisConnectionManager
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.serviceDescription
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.RedisBroadcastingStream
import dk.sdu.cloud.service.RedisDistributedStateFactory
import dk.sdu.cloud.service.configureControllers
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
        val lockFactory = DistributedLockBestEffortFactory(micro)
        val distributedStateFactory = RedisDistributedStateFactory(micro)

        val k8sClient = ReloadableKubernetesClient(
            configuration.reloadableK8Config != null && micro.developmentModeEnabled,
            listOfNotNull(configuration.reloadableK8Config).map { File(it) }
        )
        val appRole = if (micro.developmentModeEnabled) {
            "sducloud-app-dev"
        } else {
            "sducloud-app"
        }

        val k8NameAllocator = K8NameAllocator("app-kubernetes", appRole, k8sClient)
        val k8Dependencies = K8Dependencies(
            k8sClient,
            k8NameAllocator,
            micro.backgroundScope,
            serviceClient
        )

        val jobCache = VerifiedJobCache(serviceClient)
        val networkPolicyService = NetworkPolicyService(k8Dependencies)
        val hostAliasesService = HostAliasesService(k8Dependencies)
        val workspaceService = WorkspaceService(cephConfig, configuration.publicIpInterface)

        val logService = K8LogService(k8Dependencies)
        val jobMonitoringService = K8JobMonitoringService(
            k8Dependencies,
            lockFactory,
            micro.eventStreamService,
            distributedStateFactory,
            logService
        )
        val jobCreationService = K8JobCreationService(
            k8Dependencies,
            jobMonitoringService,
            networkPolicyService,
            broadcastingStream,
            hostAliasesService,
            workspaceService,
            configuration.toleration,
            devMode = micro.developmentModeEnabled
        )

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
            if (micro.developmentModeEnabled) {
                log.warn("For proxying to work you must point envoy at the configuration file mentioned above!")
            }
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
            broadcastingStream = broadcastingStream
        )
        runBlocking {
            applicationProxyService.initializeListener()
        }

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

        jobMonitoringService.initializeListeners()

        with(micro.server) {
            configureControllers(
                AppKubernetesController(
                    jobMonitoringService,
                    jobCreationService,
                    logService,
                    vncService,
                    webService,
                    broadcastingStream
                ),
                ReloadController(k8Dependencies)
            )
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
