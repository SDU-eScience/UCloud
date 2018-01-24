package dk.sdu.cloud.storage

import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.http.IRodsController
import dk.sdu.cloud.storage.processor.UserProcessor
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.response.ApplicationSendPipeline
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import stackTraceToString
import java.util.concurrent.TimeUnit

class Server(
    private val configuration: Configuration,
    private val storageService: StorageConnectionFactory,

    private val adminAccount: StorageConnection,
    private val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val zk: ZooKeeper,
    private val cloud: RefreshingJWTAuthenticator
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = StorageServiceDescription.instance(configuration.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

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

            intercept(ApplicationCallPipeline.Infrastructure) {
                if (!protect()) return@intercept finish()

                val principal = call.request.validatedPrincipal
                val connection = storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    finish()
                    return@intercept
                }
                call.attributes.put(StorageSession, connection)
            }

            sendPipeline.intercept(ApplicationSendPipeline.After) {
                call.attributes.getOrNull(StorageSession)?.close()
            }

            routing {
                route("api") {
                    // Protect is currently done through the intercept to automatically create the connection to iRODS
                    IRodsController().configure(this)
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
        log.info(instance.toString())
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }


    companion object {
        val StorageSession = AttributeKey<StorageConnection>("StorageSession")
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}
