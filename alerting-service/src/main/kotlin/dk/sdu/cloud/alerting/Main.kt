package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.api.AlertingServiceDescription
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import java.net.InetAddress
import java.net.UnknownHostException

data class Configuration (
    val notifiers: Notifiers = Notifiers(),
    val limits: Limits? = null
)

data class Limits(
    val percentLimit500Status: Double,
    val storageInfoLimit: Double,
    val storageWarnLimit: Double,
    val storageCriticalLimit: Double
)

data class Notifiers(
    val slack: SlackNotifierConfig? = null
)

data class SlackNotifierConfig(
    val hook: String
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AlertingServiceDescription, args)
        install(ElasticFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("alerting")

    Server(config, micro).start()
}
