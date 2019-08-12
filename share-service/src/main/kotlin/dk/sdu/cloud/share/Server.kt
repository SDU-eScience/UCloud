package dk.sdu.cloud.share

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
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
import dk.sdu.cloud.share.services.ProcessingService
import dk.sdu.cloud.share.services.ShareHibernateDAO
import dk.sdu.cloud.share.services.ShareQueryService
import dk.sdu.cloud.share.services.ShareService
import dk.sdu.cloud.share.services.ShareSynchronization
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        // Core services
        val shareDao = ShareHibernateDAO()
        val userClientFactory: (String) -> AuthenticatedClient = {
            RefreshingJWTAuthenticator(
                micro.client,
                it,
                micro.tokenValidation as TokenValidationJWT
            ).authenticateClient(OutgoingHttpCall)
        }

        val shareService = ShareService(
            serviceClient = client,
            db = micro.hibernateDatabase,
            shareDao = shareDao,
            userClientFactory = userClientFactory,
            devMode = micro.developmentModeEnabled,
            eventStreamService = micro.eventStreamService
        )

        val processingService =
            ProcessingService(micro.hibernateDatabase, shareDao, userClientFactory, client, shareService)

        val shareQueryService = ShareQueryService(micro.hibernateDatabase, shareDao, client)

        if (micro.commandLineArguments.contains("--sync")) {
            val service = ShareSynchronization(micro.hibernateDatabase, shareDao, userClientFactory)
            runBlocking {
                service.synchronize()
            }
            exitProcess(0)
        }

        // Processors
        StorageEventProcessor(processingService, micro.eventStreamService).init()
        shareService.initializeJobQueue()

        // Controllers
        with(micro.server) {
            configureControllers(
                ShareController(shareService, shareQueryService, client)
            )
        }

        startServices()
    }
}
