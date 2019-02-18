package dk.sdu.cloud.project.auth

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.kafka.forStream
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.project.auth.api.ProjectAuthEvents
import dk.sdu.cloud.project.auth.http.ProjectAuthController
import dk.sdu.cloud.project.auth.processors.ProjectAuthEventProcessor
import dk.sdu.cloud.project.auth.processors.ProjectEventProcessor
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.StorageInitializer
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

    override fun start() {
        // Initialize services here
        val kafka = micro.kafka
        val db = micro.hibernateDatabase
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val tokenDao: AuthTokenDao<HibernateSession> = AuthTokenHibernateDao()
        val tokenInvalidator = TokenInvalidator(client, db, tokenDao)
        val tokenRefresher = TokenRefresher(client, db, tokenDao, tokenInvalidator)

        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val storageInitializer = StorageInitializer(
            refreshTokenCloudFactory = { refreshToken ->
                client.withoutAuthentication().bearerAuth(refreshToken)
            }
        )

        // Initialize consumers here:
        ProjectEventProcessor(
            client,
            db,
            tokenDao,
            tokenInvalidator,
            kafka,
            kafka.producer.forStream(ProjectAuthEvents.events)
        ).apply {
            addConsumers(init())
        }

        ProjectAuthEventProcessor(
            db,
            tokenDao,
            kafka
        ).apply {
            addListener(storageInitializer)

            addConsumers(init())
        }

        // Initialize server
        with(micro.server) {
            configureControllers(
                ProjectAuthController(tokenRefresher, client)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
