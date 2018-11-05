package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.indexing.api.IndexingServiceDescription
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.serviceInstance
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * A host:port pair for Elasticsearch
 */
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
        initWithDefaultFeatures(IndexingServiceDescription, args)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val elasticLocation = micro.configuration.requestChunkAtOrNull("elastic") ?: ElasticHostAndPort.guessDefaults()

    Server(
        elasticLocation,
        micro.kafka,
        micro.serverProvider,
        micro.refreshingJwtCloud,
        args,
        micro.serviceInstance
    ).start()
}
