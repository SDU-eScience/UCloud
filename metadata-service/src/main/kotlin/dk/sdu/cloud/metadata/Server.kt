package dk.sdu.cloud.metadata

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.metadata.api.MetadataServiceDescription
import dk.sdu.cloud.metadata.api.ProjectEvents
import dk.sdu.cloud.metadata.http.MetadataController
import dk.sdu.cloud.metadata.http.ProjectsController
import dk.sdu.cloud.metadata.processor.StorageEventProcessor
import dk.sdu.cloud.metadata.services.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageEvents
import io.ktor.application.install
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class Server(
    private val configuration: Configuration,
    private val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val serviceRegistry: ServiceRegistry,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = MetadataServiceDescription.instance(configuration.connConfig)

        log.info("Creating core services")
        val projectService = ProjectService(ProjectSQLDao())
        val elasticMetadataService =
            with(configuration.elastic) { ElasticMetadataService(hostname, port, scheme, projectService) }

        if (args.contains("--init-db")) {
            log.info("Initializing database")
            transaction {
                drop(Projects)
                create(Projects)
            }
            exitProcess(0)
        } else if (args.contains("--init-elastic")) {
            log.info("Initializing elastic search")
            elasticMetadataService.initializeElasticSearch()
            exitProcess(0)
        }

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            StorageEventProcessor(
                kBuilder.stream(StorageEvents.events),
                kBuilder.stream(ProjectEvents.events),
                elasticMetadataService,
                projectService,
                cloud
            ).init()
            log.info("Stream processors configured!")

            kafka.build(kBuilder.build()).also {
                log.info("Kafka Streams Topology successfully built!")
            }
        }

        kStreams.setUncaughtExceptionHandler { _, exception ->
            log.error("Caught fatal exception in Kafka! Stacktrace follows:")
            log.error(exception.stackTraceToString())
            stop()
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(JWTProtection)

            routing {
                route("api") {
                    protect()

                    route("projects") {
                        ProjectsController(
                            kafka.producer.forStream(ProjectEvents.events),
                            projectService
                        ).configure(this)
                    }

                    route("metadata") {
                        MetadataController(
                            elasticMetadataService,
                            elasticMetadataService,
                            elasticMetadataService,
                            projectService
                        ).configure(this)
                    }
                }
            }

            log.info("HTTP server successfully configured!")
        }

        log.info("Starting HTTP server...")
        httpServer.start(wait = false)
        log.info("HTTP server started!")

        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")

        serviceRegistry.register(listOf("/api/metadata", "/api/projects"))
        log.info("Server is ready!")
        log.info(instance.toString())
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}