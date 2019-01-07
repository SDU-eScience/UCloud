package dk.sdu.cloud.project.auth

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.project.auth.http.ProjectAuthController
import dk.sdu.cloud.project.auth.processors.ProjectEventProcessor
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
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
        val db = micro.hibernateDatabase
        val tokenDao: AuthTokenDao<HibernateSession> = AuthTokenHibernateDao()
        val tokenInvalidator = TokenInvalidator(cloud, db, tokenDao)
        val tokenRefresher = TokenRefresher(cloud, db, tokenDao, tokenInvalidator)

        // Initialize consumers here:
        addConsumers(
            ProjectEventProcessor(
                cloud,
                db,
                tokenDao,
                tokenInvalidator,
                kafka
            ).init()
        )

        // Initialize server
        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    ProjectAuthController(tokenRefresher, cloud.parent)
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
