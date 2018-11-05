package dk.sdu.cloud.notification

import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.NotificationHibernateDAO
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    private val db: HibernateSessionFactory,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override var kStreams: KafkaStreams? = null

    override fun start() {
        log.info("Creating core services")
        val notificationDao = NotificationHibernateDAO()
        log.info("Core services constructed!")

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(micro)

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
