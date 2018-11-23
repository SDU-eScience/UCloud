package dk.sdu.cloud.share

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.tokenValidation
import dk.sdu.cloud.share.http.ShareController
import dk.sdu.cloud.share.processors.StorageEventProcessor
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareService
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val micro: Micro
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

    override fun start() {
        // Initialize services here:
        val jwtValidation = micro.tokenValidation as TokenValidationJWT
        val shareDao = ShareHibernateDAO()
        val shareService = ShareService(
            serviceCloud = cloud,
            db = micro.hibernateDatabase,
            shareDao = shareDao,
            userCloudFactory = { RefreshingJWTAuthenticatedCloud(cloud.parent, it, jwtValidation) }
        )

        // Initialize consumers here:
        addConsumers(
            StorageEventProcessor(shareService, kafka).init()
        )

        // Initialize server:
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    ShareController(shareService, cloud.parent)
                )
            }
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
