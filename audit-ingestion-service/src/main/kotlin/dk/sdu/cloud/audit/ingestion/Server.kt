package dk.sdu.cloud.audit.ingestion

import dk.sdu.cloud.audit.ingestion.processors.AuditProcessor
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import java.net.InetAddress
import java.net.UnknownHostException

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


class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val elasticHostAndPort = ElasticHostAndPort.guessDefaults()
        val client = RestHighLevelClient(
            RestClient.builder(
                HttpHost(
                    elasticHostAndPort.host,
                    elasticHostAndPort.port,
                    "http"
                )
            ).setMaxRetryTimeoutMillis(300000)
        )

        AuditProcessor(micro.eventStreamService, client).init()

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
