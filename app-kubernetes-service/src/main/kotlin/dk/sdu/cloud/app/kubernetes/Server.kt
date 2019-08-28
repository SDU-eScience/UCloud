package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.install
import io.ktor.routing.routing

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

        val sharedFileSystemMountService = SharedFileSystemMountService()
        val podService = PodService(
            k8sClient,
            serviceClient,
            networkPolicyService,
            appRole = appRole,
            sharedFileSystemMountService = sharedFileSystemMountService,
            hostAliasesService = hostAliasesService
        )

        val authenticationService = AuthenticationService(serviceClient, micro.tokenValidation)
        tunnelManager = TunnelManager(podService)
        tunnelManager.install()

        val vncService = VncService(tunnelManager)
        val webService = WebService(
            authenticationService = authenticationService,
            tunnelManager = tunnelManager,
            performAuthentication = configuration.performAuthentication,
            prefix = configuration.prefix,
            domain = configuration.domain,
            cookieName = configuration.cookieName
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
