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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class Server(
    private val db: HibernateSessionFactory,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val instance: ServiceInstance,
    private val cloud: RefreshingJWTAuthenticatedCloud
): CommonServer {
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override var kStreams: KafkaStreams? = null

    override fun start() {
        log.info("Creating core services")
        val notificationDao = NotificationHibernateDAO()
        log.info("Core services constructed!")

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance, requireJobId = true)
            install(JWTProtection)

            routing {
                configureControllers(
                    NotificationController(
                        db,
                        notificationDao
                    )
                )
            }

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }
}