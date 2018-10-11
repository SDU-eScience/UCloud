package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.indexing.http.LookupController
import dk.sdu.cloud.indexing.http.SearchController
import dk.sdu.cloud.indexing.processor.StorageEventProcessor
import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.services.FileIndexScanner
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
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

            routing {
                configureControllers(
                    SearchController(queryService),
                    LookupController(queryService)
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
