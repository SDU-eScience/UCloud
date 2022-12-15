package dk.sdu.cloud.micro

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.RestHighLevelClientBuilder
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

class ElasticFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        val shouldLog = didLog.compareAndSet(false, true)
        val configuration = ctx.configuration.requestChunkAtOrNull(*CONFIG_PATH) ?: run {
            if (shouldLog) log.trace(
                "No elastic configuration provided at ${CONFIG_PATH.toList()}. " +
                        "Using default localhost settings (not secured cluster)"
            )

            Config()
        }

        val credentialsProvider = BasicCredentialsProvider()
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            UsernamePasswordCredentials(
                configuration.credentials?.username ?: "",
                configuration.credentials?.password ?: "")
        )
        val lowLevelClient = RestClient.builder(
            HttpHost(
                configuration.hostname,
                configuration.port!!,
                "http"
            )
        )
            .setHttpClientConfigCallback { httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(
                    credentialsProvider
                )
            }
            .setRequestConfigCallback { requestConfigBuilder ->
                requestConfigBuilder
                    .setConnectTimeout(30000)
                    .setSocketTimeout(60000)
            }

        ctx.elasticLowLevelClient = lowLevelClient.build()

        val transport = RestClientTransport(
            ctx.elasticLowLevelClient,
            JacksonJsonpMapper()
        )

        ctx.elasticClient = ElasticsearchClient(transport)

        if (shouldLog) {
            log.info("Connected to elasticsearch at ${configuration.hostname}. " +
                "Config is loaded from ${CONFIG_PATH.joinToString("/")}.")
        }
    }

    companion object Feature : MicroFeatureFactory<ElasticFeature, Unit>, Loggable {

        override val key = MicroAttributeKey<ElasticFeature>("elastic-feature")
        override fun create(config: Unit): ElasticFeature = ElasticFeature()
        override val log = logger()

        internal val CLIENT = MicroAttributeKey<ElasticsearchClient>("elastic-client")
        internal val HIGH_LEVEL = MicroAttributeKey<RestHighLevelClient>("elastic-high-level-client")
        internal val LOW_LEVEL = MicroAttributeKey<RestClient>("elastic-low-level-client")

        private fun testHostname(hostname: String): Boolean {
            return try {
                InetAddress.getByName(hostname)
                true
            } catch (ex: UnknownHostException) {
                false
            }
        }

        fun findValidHostname(hostnames: List<String>): String? {
            return hostnames.find { testHostname(it) }
        }

        //Config chunks
        val CONFIG_PATH = arrayOf("elk", "elasticsearch")

        data class Credentials(val username: String, val password: String)

        data class Config(
            val hostname: String? = findValidHostname(listOf("elasticsearch", "localhost")),
            val port: Int? = 9200,
            val credentials: Credentials? = null
        )

        private val didLog = AtomicBoolean(false)
    }
}

var Micro.elasticClient: ElasticsearchClient
    get() {
        requireFeature(ElasticFeature)
        return attributes[ElasticFeature.CLIENT]
    }
    internal set(value) {
        attributes[ElasticFeature.CLIENT] = value
    }

var Micro.elasticLowLevelClient: RestClient
    get() {
        requireFeature(ElasticFeature)
        return attributes[ElasticFeature.LOW_LEVEL]
    }
    internal set(value) {
        attributes[ElasticFeature.LOW_LEVEL] = value
    }
