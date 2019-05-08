package dk.sdu.cloud.web

import dk.sdu.cloud.micro.ClientFeature
import dk.sdu.cloud.micro.ConfigurationFeature
import dk.sdu.cloud.micro.DevelopmentOverrides
import dk.sdu.cloud.micro.KafkaFeature
import dk.sdu.cloud.micro.KafkaFeatureConfiguration
import dk.sdu.cloud.micro.KafkaTopicFeature
import dk.sdu.cloud.micro.KafkaTopicFeatureConfiguration
import dk.sdu.cloud.micro.KtorServerProviderFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ScriptFeature
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.ServiceInstanceFeature
import dk.sdu.cloud.micro.TokenValidationFeature
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.web.api.WebServiceDescription

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(WebServiceDescription, args)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro
    ).start()
}
