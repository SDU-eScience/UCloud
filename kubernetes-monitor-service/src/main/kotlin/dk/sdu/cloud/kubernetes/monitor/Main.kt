package dk.sdu.cloud.kubernetes.monitor

import dk.sdu.cloud.kubernetes.monitor.api.KubernetesMonitorServiceDescription
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

data class Configuration (
    val notifiers: Notifiers
)

data class Notifiers(
    val slack: SlackNotifierConfig
)

data class SlackNotifierConfig(
    val hook: String
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(KubernetesMonitorServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("alerting")

    Server(config, micro).start()
}
