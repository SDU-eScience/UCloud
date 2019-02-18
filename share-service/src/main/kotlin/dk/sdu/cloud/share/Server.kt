package dk.sdu.cloud.share

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.share.http.ShareController
import dk.sdu.cloud.share.processors.StorageEventProcessor
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareService

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
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val kafka = micro.kafka

        val shareDao = ShareHibernateDAO()
        val shareService = ShareService(
            serviceCloud = client,
            db = micro.hibernateDatabase,
            shareDao = shareDao,
            userCloudFactory = {
                RefreshingJWTAuthenticator(
                    micro.client,
                    it,
                    micro.tokenValidation as TokenValidationJWT
                ).authenticateClient(OutgoingHttpCall)
            },
            devMode = micro.developmentModeEnabled
        )

        // Initialize consumers here:
        addConsumers(
            StorageEventProcessor(shareService, kafka).init()
        )

        // Initialize server:
        with(micro.server) {
            configureControllers(
                ShareController(shareService, client)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
