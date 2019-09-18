package dk.sdu.cloud.app.kubernetes.watcher

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.kubernetes.watcher.services.JobWatcher
import io.fabric8.kubernetes.client.DefaultKubernetesClient

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val k8sClient = DefaultKubernetesClient()

        val appRole = if (micro.developmentModeEnabled) {
            "sducloud-app-dev"
        } else {
            "sducloud-app"
        }

        val watcher = JobWatcher(k8sClient, micro.eventStreamService, appRole, "app-kubernetes")
        watcher.startWatch()

        startServices()
    }
}
