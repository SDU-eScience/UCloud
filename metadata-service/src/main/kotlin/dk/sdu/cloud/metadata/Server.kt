package dk.sdu.cloud.metadata

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.metadata.api.ProjectEvents
import dk.sdu.cloud.metadata.http.MetadataController
import dk.sdu.cloud.metadata.http.ProjectsController
import dk.sdu.cloud.metadata.processor.StorageEventProcessor
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.ProjectHibernateDAO
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.stream
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.http.HttpHost
import org.apache.kafka.streams.KafkaStreams
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

class Server(
    private val db: HibernateSessionFactory,
    private val configuration: ElasticHostAndPort,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>,
    private val instance: ServiceInstance
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override val log = logger()

    override fun start() {
        log.info("Creating core services")
        val projectService = ProjectService(db, ProjectHibernateDAO())
        val elasticMetadataService =
            with(configuration) {
                ElasticMetadataService(
                    RestHighLevelClient(RestClient.builder(HttpHost(host, port, "http"))),
                    projectService
                )
            }

        if (args.contains("--init-elastic")) {
            log.info("Initializing elastic search")
            elasticMetadataService.initializeElasticSearch()
            exitProcess(0)
        }

        kStreams = buildStreams { kBuilder ->
            StorageEventProcessor(
                kBuilder.stream(StorageEvents.events),
                kBuilder.stream(ProjectEvents.events),
                elasticMetadataService,
                projectService,
                cloud
            ).init()
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)

            routing {
                configureControllers(
                    ProjectsController(
                        kafka.producer.forStream(ProjectEvents.events),
                        projectService
                    ),
                    MetadataController(
                        elasticMetadataService,
                        elasticMetadataService,
                        elasticMetadataService,
                        projectService
                    )
                )
            }

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }
}
