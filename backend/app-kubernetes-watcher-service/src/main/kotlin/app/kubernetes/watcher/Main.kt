package dk.sdu.cloud.app.kubernetes.watcher

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.kubernetes.watcher.api.AppKubernetesWatcherServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AppKubernetesWatcherService : Service {
    override val description = AppKubernetesWatcherServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AppKubernetesWatcherService.runAsStandalone(args)
}
