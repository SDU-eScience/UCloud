package dk.sdu.cloud.storage

import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.ICAT
import dk.sdu.cloud.storage.http.ACLController
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.http.SimpleDownloadController
import dk.sdu.cloud.storage.processor.UserProcessor
import io.ktor.application.install
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class Server(
    private val configuration: Configuration,
    private val storageService: StorageConnectionFactory,

    private val adminAccount: StorageConnection,
    private val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val serviceRegistry: ServiceRegistry,
    private val cloud: RefreshingJWTAuthenticator
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = StorageServiceDescription.instance(configuration.connConfig)

        val icat = ICAT(configuration.icat)

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            UserProcessor(kBuilder.stream(AuthStreams.UserUpdateStream), adminAccount).init()
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
                    FilesController(storageService, icat, configuration.icat.defaultZone).configure(this)
                    SimpleDownloadController(cloud, storageService).configure(this)
                    ACLController(storageService).configure(this)
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

        serviceRegistry.register(listOf("/api/files"))
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
