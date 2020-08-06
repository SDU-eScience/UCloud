package dk.sdu.cloud.app.kubernetes.watcher

import dk.sdu.cloud.app.kubernetes.watcher.rpc.ReloadController
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.kubernetes.watcher.services.JobWatcher
import dk.sdu.cloud.app.kubernetes.watcher.services.ReloadableKubernetesClient
import java.io.File

class Server(
    override val micro: Micro,
    private val configuration: Configuration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val k8sClient = ReloadableKubernetesClient(
            configuration.reloadableK8Config != null && micro.developmentModeEnabled,
            listOfNotNull(configuration.reloadableK8Config).map { File(it) }
        )

        val appRole = if (micro.developmentModeEnabled) {
            "sducloud-app-dev"
        } else {
            "sducloud-app"
        }

        val watcher = JobWatcher(k8sClient, micro.eventStreamService, appRole, "app-kubernetes")
        if (!k8sClient.allowReloading) { // lazily initialized for testing to prevent errors during startup
            watcher.startWatch()
        }

        with (micro.server) {
            configureControllers(ReloadController(k8sClient, watcher))
        }

        startServices()
    }
}
