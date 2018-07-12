package dk.sdu.cloud.notification

import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.InMemoryNotificationDAO
import dk.sdu.cloud.notification.services.NotificationDAO
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.notification.api.NotificationServiceDescription
import dk.sdu.cloud.notification.services.NotificationHibernateDAO
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.application.install
import io.ktor.routing.contentType
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class Server(
    private val db: HibernateSessionFactory,
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
        val instance = NotificationServiceDescription.instance(configuration.connConfig)

        log.info("Creating core services")
        val notificationDao = NotificationHibernateDAO()
        log.info("Core services constructed!")

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
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
            installDefaultFeatures(cloud, kafka, instance, requireJobId = true)
            install(JWTProtection)

            routing {
                route("api/notifications") {
                    NotificationController(db, notificationDao).configure(this)
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

        serviceRegistry.register(listOf("/api/notification"))
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