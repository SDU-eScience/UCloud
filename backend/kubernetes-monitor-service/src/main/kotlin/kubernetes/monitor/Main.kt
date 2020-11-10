package dk.sdu.cloud.kubernetes.monitor

import dk.sdu.cloud.kubernetes.monitor.api.KubernetesMonitorServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object KubernetesMonitorService : Service {
    override val description = KubernetesMonitorServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(KubernetesMonitorServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

}
