package dk.sdu.cloud.file.favorite

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.file.favorite.http.FileFavoriteController
import dk.sdu.cloud.file.favorite.services.FileFavoriteHibernateDAO
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val db: HibernateSessionFactory,
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
        // Initialize services here
        val fileFavoriteDao = FileFavoriteHibernateDAO()
        val fileFavoriteService = FileFavoriteService(db, fileFavoriteDao)

        // Initialize consumers here:
        // addConsumers(...)

        // Initialize server
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    FileFavoriteController(fileFavoriteService, cloud.parent)
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
