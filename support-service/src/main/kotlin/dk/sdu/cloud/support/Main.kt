package dk.sdu.cloud.support

import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.service.CloudFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
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
        feature(CloudFeature).addAuthenticatedCloud(0, cloudContext.jwtAuth(""))
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("ticket")

    Server(
        micro.kafka,
        micro.serverProvider,
        micro,
        config
    ).start()
}
