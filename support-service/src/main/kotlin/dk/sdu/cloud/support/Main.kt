package dk.sdu.cloud.support

import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

import dk.sdu.cloud.support.api.SupportServiceDescription

data class Configuration(
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
        initWithDefaultFeatures(SupportServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("ticket")
    Server(
        micro,
        config
    ).start()
}
