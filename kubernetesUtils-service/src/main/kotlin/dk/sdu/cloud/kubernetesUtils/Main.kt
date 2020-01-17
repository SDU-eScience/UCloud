package dk.sdu.cloud.kubernetesUtils

import dk.sdu.cloud.kubernetesUtils.api.KubernetesUtilsServiceDescription
import dk.sdu.cloud.micro.*

data class Configuration (
    val notifiers: Notifiers = Notifiers()
)

data class Notifiers(
    val slack: SlackNotifierConfig? = null
)

data class SlackNotifierConfig(
    val hook: String
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(KubernetesUtilsServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("alerting")

    Server(config, micro).start()
}
