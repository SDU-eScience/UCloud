package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.ApplicationProxyService
import dk.sdu.cloud.app.kubernetes.services.AuthenticationService
import dk.sdu.cloud.app.kubernetes.services.EnvoyConfigurationService
import dk.sdu.cloud.app.kubernetes.services.HostAliasesService
import dk.sdu.cloud.app.kubernetes.services.NetworkPolicyService
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.SharedFileSystemMountService
import dk.sdu.cloud.app.kubernetes.services.TunnelManager
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.serviceInstance
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.routing.routing
import java.io.File

class Server(override val micro: Micro, private val configuration: Configuration) : CommonServer {
    override val log = logger()
    lateinit var tunnelManager: TunnelManager

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val k8sClient = DefaultKubernetesClient()
        val appRole = if (micro.developmentModeEnabled) {
            "sducloud-app-dev"
        } else {
            "sducloud-app"
        }

        val networkPolicyService = NetworkPolicyService(
            k8sClient,
            appRole = appRole
        )

        val hostAliasesService = HostAliasesService(k8sClient, appRole = appRole)
        val envoyConfigurationService = EnvoyConfigurationService(
            File("./envoy/rds.yaml"),
            File("./envoy/clusters.yaml")
        )

        run {
            val port = micro.featureOrNull(ServiceDiscoveryOverrides)?.get(micro.serviceDescription.name)?.port
                ?: 8080
            val renderedConfig =
                Server::class.java.classLoader.getResourceAsStream("config_template.yaml")
                    .bufferedReader()
                    .readText()
                    .replace("\$SERVICE_PORT", port.toString())
                    .replace("\$PWD", File("./envoy").absoluteFile.normalize().absolutePath)

            val configFile = File("./envoy/config.yaml")
            configFile.writeText(renderedConfig)
            log.info("Wrote configuration at ${configFile.absolutePath}")
            if (micro.developmentModeEnabled) {
                log.warn("For proxying to work you must point envoy at the configuration file mentioned above!")
            }
        }

        val applicationProxyService = ApplicationProxyService(
            envoyConfigurationService,
            prefix = configuration.prefix,
            domain = configuration.domain
        )

        val sharedFileSystemMountService = SharedFileSystemMountService()
        val podService = PodService(
            k8sClient,
            serviceClient,
            networkPolicyService,
            appRole = appRole,
            sharedFileSystemMountService = sharedFileSystemMountService,
            hostAliasesService = hostAliasesService,
            applicationProxyService = applicationProxyService
        )

        val authenticationService = AuthenticationService(serviceClient, micro.tokenValidation)
        tunnelManager = TunnelManager(podService)
        tunnelManager.install()

        val vncService = VncService(tunnelManager)
        val webService = WebService(
            serviceClient = serviceClient,
            authenticationService = authenticationService,
            tunnelManager = tunnelManager,
            performAuthentication = configuration.performAuthentication,
            cookieName = configuration.cookieName,
            applicationProxyService = applicationProxyService,
            prefix = configuration.prefix,
            domain = configuration.domain
        )

        podService.initializeListeners()

        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService, vncService, webService)
            )
        }

        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!

        startServices(wait = false)

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
