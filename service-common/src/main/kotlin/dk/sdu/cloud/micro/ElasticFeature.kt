package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient

class ElasticFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        val configuration = ctx.configuration.requestChunkAtOrNull(*CONFIG_PATH) ?: run {
            log.warn(
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

        ctx.elasticLowLevelClient = lowLevelClient.build()

        ctx.elasticHighLevelClient = RestHighLevelClient(
            lowLevelClient
        )
    }

    companion object Feature : MicroFeatureFactory<ElasticFeature, Unit>, Loggable {

        override val key = MicroAttributeKey<ElasticFeature>("elastic-feature")
        override fun create(config: Unit): ElasticFeature = ElasticFeature()
        override val log = logger()

        internal val HIGH_LEVEL = MicroAttributeKey<RestHighLevelClient>("elastic-high-level-client")
        internal val LOW_LEVEL = MicroAttributeKey<RestClient>("elastic-low-level-client")

        //Config chunks
        val CONFIG_PATH = arrayOf("elk", "elasticsearch")

        data class Credentials(val username: String, val password: String)

        data class Config(
            val hostname: String? = "localhost",
            val port: Int? = 9200,
            val credentials: Credentials? = null
        )

    }
}

var Micro.elasticHighLevelClient: RestHighLevelClient
    get() {
        requireFeature(ElasticFeature)
        return attributes[ElasticFeature.HIGH_LEVEL]
    }
    internal set(value) {
        attributes[ElasticFeature.HIGH_LEVEL] = value
    }

var Micro.elasticLowLevelClient: RestClient
    get() {
        requireFeature(ElasticFeature)
        return attributes[ElasticFeature.LOW_LEVEL]
    }
    internal set(value) {
        attributes[ElasticFeature.LOW_LEVEL] = value
    }
