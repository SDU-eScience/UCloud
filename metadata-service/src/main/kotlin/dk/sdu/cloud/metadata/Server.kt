package dk.sdu.cloud.metadata

import dk.sdu.cloud.metadata.http.MetadataController
import dk.sdu.cloud.metadata.http.ProjectsController
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.authenticatedCloud
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
    private val configuration: ElasticHostAndPort,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val micro: Micro
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null

    override val log = logger()

    override fun start() {
        log.info("Creating core services")
        val projectService = ProjectService(micro.authenticatedCloud)
        val elasticMetadataService =
            with(configuration) {
                ElasticMetadataService(
                    RestHighLevelClient(RestClient.builder(HttpHost(host, port, "http"))),
                    micro.authenticatedCloud
                )
            }

        if (micro.commandLineArguments.contains("--init-elastic")) {
            log.info("Initializing elastic search")
            elasticMetadataService.initializeElasticSearch()
            exitProcess(0)
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    ProjectsController(
                        projectService,
                        elasticMetadataService
                    ),
                    MetadataController(
                        elasticMetadataService,
                        elasticMetadataService,
                        elasticMetadataService
                    )
                )
            }

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }
}
