package dk.sdu.cloud.kubernetes.monitor

import dk.sdu.cloud.kubernetes.monitor.api.KubernetesMonitorServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class Configuration (
    val notifiers: Notifiers
)

data class Notifiers(
    val slack: SlackNotifierConfig
)

data class SlackNotifierConfig(
    val hook: String
)

object KubernetesMonitorService : Service {
    override val description = KubernetesMonitorServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        val config = micro.configuration.requestChunkAt<Configuration>("alerting")
        return Server(config, micro)
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(KubernetesMonitorServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

}
