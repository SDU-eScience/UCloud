package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.indexing.http.SearchController
import dk.sdu.cloud.indexing.processor.StorageEventProcessor
import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.services.FileIndexScanner
import dk.sdu.cloud.service.*
import io.ktor.application.install
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.http.HttpHost
import org.apache.kafka.streams.KafkaStreams
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

class Server(
    private val elasticHostAndPort: ElasticHostAndPort,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>,
    private val instance: ServiceInstance
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams
    private val eventConsumers = ArrayList<EventConsumer<*>>()
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()
//    override val endpoints: List<String> = listOf("/api/file-search", "/api/index")

    override fun start() {
        elastic = RestHighLevelClient(
            RestClient.builder(
                HttpHost(
                    elasticHostAndPort.host,
                    elasticHostAndPort.port,
                    "http"
                )
            )
        )
        val indexingService = ElasticIndexingService(elastic)
        val queryService = ElasticQueryService(elastic)

        if (args.contains("--scan")) {
            try {
                val scanner = FileIndexScanner(cloud, elastic)
                scanner.scan()

                exitProcess(0)
            } catch (ex: Exception) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        StorageEventProcessor(kafka, indexingService).init().forEach { processor ->
            processor.onExceptionCaught { stop() }
        }

        kStreams = buildStreams { }
        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance, requireJobId = true)
            install(JWTProtection)

            routing {
                configureControllers(
                    SearchController(queryService)
                )
            }
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
        elastic.close()
    }
}