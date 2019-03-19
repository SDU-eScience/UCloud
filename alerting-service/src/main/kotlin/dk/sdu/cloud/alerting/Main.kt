package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.api.AlertingServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
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

data class ElasticHostAndPort(
    val host: String,
    val port: Int = 9200
) {
    companion object {
        fun guessDefaults() =
            ElasticHostAndPort(
                host = findValidHostname(listOf("elasticsearch", "localhost"))!!,
                port = 9200
            )

        private fun testHostname(hostname: String): Boolean {
            return try {
                InetAddress.getByName(hostname)
                true
            } catch (ex: UnknownHostException) {
                false
            }
        }

        private fun findValidHostname(hostnames: List<String>): String? {
            return hostnames.find { testHostname(it) }
        }
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AlertingServiceDescription, args)
    }

    if (micro.runScriptHandler()) return

    val elasticLocation = micro.configuration.requestChunkAtOrNull("elastic") ?: ElasticHostAndPort.guessDefaults()
    val config = micro.configuration.requestChunkAt<Configuration>("alerting")

    Server(elasticLocation, config, micro).start()
}
