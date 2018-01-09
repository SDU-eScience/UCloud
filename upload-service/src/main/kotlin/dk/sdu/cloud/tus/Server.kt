package dk.sdu.cloud.tus

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.*
import dk.sdu.cloud.tus.api.TusServiceDescription
import dk.sdu.cloud.tus.api.internal.TusStreams
import dk.sdu.cloud.tus.http.TusController
import dk.sdu.cloud.tus.processors.UploadStateProcessor
import dk.sdu.cloud.tus.services.ICAT
import dk.sdu.cloud.tus.services.RadosStorage
import dk.sdu.cloud.tus.services.TransferStateService
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import stackTraceToString
import java.io.File
import java.util.concurrent.TimeUnit

class Server(
        private val configuration: Configuration,
        private val kafka: KafkaServices,
        private val zk: ZooKeeper,
        private val ktor: HttpServerProvider,
        @Suppress("unused") private val cloud: RefreshingJWTAuthenticator
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = TusServiceDescription.instance(configuration.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

        log.info("Creating core services")
        val rados = RadosStorage("client.irods", File("ceph.conf"), "irods")
        val transferState = TransferStateService()
        val icat = ICAT(configuration.database)
        val tus = TusController(
                config = configuration.database,
                rados = rados,
                producer = kafka.producer.forStream(TusStreams.UploadEvents),
                transferState = transferState
        )
        log.info("Core services constructed!")

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            UploadStateProcessor(
                    TusStreams.UploadEvents.stream(kBuilder),
                    transferState,
                    icat
            ).also { it.init() }
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

            installDefaultFeatures()

            routing {
                route("api/tus") {
                    protect()

                    tus.registerTusEndpoint(this, "/api/tus")
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

        runBlocking { zk.markServiceAsReady(node, instance) }
        log.info("Server is ready!")
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}
