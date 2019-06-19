package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.api.ApplicationMetadata
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.ToolReference
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.AuthenticationService
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
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.install
import io.ktor.routing.routing
import java.util.*

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

        val sharedFileSystemMountService = SharedFileSystemMountService()
        val podService = PodService(
            k8sClient,
            serviceClient,
            networkPolicyService,
            appRole = appRole,
            sharedFileSystemMountService = sharedFileSystemMountService
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

        if (micro.commandLineArguments.contains("--testing")) {
            podService.create(
                VerifiedJob(
                    Application(
                        ApplicationMetadata(
                            "test-app",
                            "0.1.0",
                            emptyList(),
                            "testapp",
                            "",
                            emptyList(),
                            null
                        ),
                        ApplicationInvocationDescription(
                            ToolReference(
                                "tool", "1.0.0", Tool(
                                    "owner",
                                    0L,
                                    0L,
                                    NormalizedToolDescription(
                                        NameAndVersion("tool", "1.0.0"),
                                        "alpine:latest",
                                        1,
                                        1,
                                        SimpleDuration(1, 0, 0),
                                        emptyList(),
                                        emptyList(),
                                        "",
                                        "",
                                        ToolBackend.DOCKER,
                                        ""
                                    )
                                )
                            ),
                            listOf(WordInvocationParameter("sleep"), WordInvocationParameter("1000")),
                            emptyList(),
                            emptyList()
                        )
                    ),
                    emptyList(),
                    UUID.randomUUID().toString(),
                    "no-owner",
                    2,
                    1,
                    SimpleDuration(0, 30, 0),
                    VerifiedJobInput(emptyMap()),
                    "kubernetes",
                    JobState.VALIDATED,
                    "",
                    "testing",
                    1,
                    workspace = "not-a-real-workspace"
                )
            )

            return
        }

        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService, vncService, webService)
            )
        }

        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
        ktorEngine.application.install(io.ktor.websocket.WebSockets)
        ktorEngine.application.routing {
            vncService.install(this)
            webService.install(this)
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        tunnelManager.shutdown()
    }
}
