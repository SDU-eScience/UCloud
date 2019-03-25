package dk.sdu.cloud.share

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.share.http.ShareController
import dk.sdu.cloud.share.processors.StorageEventProcessor
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareService

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
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
        StorageEventProcessor(shareService, micro.eventStreamService).init()

        // Initialize server:
        with(micro.server) {
            configureControllers(
                ShareController(shareService, client)
            )
        }

        startServices()
    }
}
