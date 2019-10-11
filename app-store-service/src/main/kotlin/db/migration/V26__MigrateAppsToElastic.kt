package db.migration

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.findValidHostname
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context


@Suppress("ClassNaming", "NestedBlockDepth")
class V26__MigrateAppsToElastic : BaseJavaMigration() {

    val CONFIG_PATH = arrayOf("elk", "elasticsearch")

    data class Credentials(val username: String, val password: String)

    data class Config(
        val hostname: String? = findValidHostname(listOf("elasticsearch", "localhost")),
        val port: Int? = 9200,
        val credentials: Credentials? = null
    )

    private fun ObjectMapper.basicConfig() {
        registerKotlinModule()
        configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true)
        configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    }

    private fun <T> requestChunkAt(
        valueTypeRef: com.fasterxml.jackson.core.type.TypeReference<T>,
        vararg path: String
    ): T {
        val jsonMapper = ObjectMapper().apply { basicConfig() }
        val tree = jsonMapper.readTree("{}")

        val jsonNode = tree.at("/" + path.joinToString("/"))?.takeIf { !it.isMissingNode }
            ?: throw Exception(path.joinToString("/"))
        return jsonNode.traverse(jsonMapper).readValueAs<T>(valueTypeRef)
    }


    private inline fun <reified T : Any> requestChunkAt(vararg path: String): T {
        return requestChunkAt(jacksonTypeRef(), *path)
    }

    private inline fun <reified T : Any> requestChunkAtOrNull(vararg path: String): T? {
        return try {
            requestChunkAt(*path)
        } catch (ex: dk.sdu.cloud.micro.ServerConfigurationException.MissingNode) {
            null
        }
    }


    override fun migrate(context: Context) {
        val connection = context.connection
        connection.autoCommit = false


        val configuration = requestChunkAtOrNull(*CONFIG_PATH) ?: run {
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
                configuration.credentials?.username,
                configuration.credentials?.password
            )
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

        val highLevelClient = RestHighLevelClient(lowLevelClient)

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name, version, description, title FROM applications;").use { row ->
                while (row.next()) {
                    val name = row.getString(1)
                    val version = row.getString(2)
                    val description = row.getString(3)
                    val title = row.getString(4)


                    val elasticDAO = ElasticDAO(highLevelClient)
                    elasticDAO.createApplicationInElastic(name, version, description, title)
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

