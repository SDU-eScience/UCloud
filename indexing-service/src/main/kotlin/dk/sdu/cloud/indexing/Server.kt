package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.indexing.api.IndexingServiceDescription
import dk.sdu.cloud.indexing.processor.StorageEventProcessor
import dk.sdu.cloud.indexing.services.IndexingService
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageEvents
import io.ktor.application.install
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.http.HttpHost
import org.apache.kafka.streams.KafkaStreams
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient

class Server(
    private val configuration: Configuration,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    override val serviceRegistry: ServiceRegistry,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>
) : CommonServer, WithServiceRegistry {
    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override val log = logger()
    override val endpoints: List<String> = listOf("/api/search", "/api/index")

    override fun start() {
        val instance = IndexingServiceDescription.instance(configuration.connConfig)

        val elastic = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
        val indexingService = IndexingService(elastic)

        kStreams = buildStreams { kBuilder ->
            StorageEventProcessor(kBuilder.stream(StorageEvents.events), indexingService).also { it.init() }
        }

        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance, requireJobId = true)
            install(JWTProtection)

            routing {

            }
        }

        startServices()
        registerWithRegistry()
    }
}