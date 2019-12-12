package dk.sdu.cloud.share

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.share.http.ShareController
import dk.sdu.cloud.share.processors.StorageEventProcessor
import dk.sdu.cloud.share.services.ProcessingService
import dk.sdu.cloud.share.services.ShareAsyncDao
import dk.sdu.cloud.share.services.ShareQueryService
import dk.sdu.cloud.share.services.ShareService
import dk.sdu.cloud.share.services.ShareSynchronization
import dk.sdu.cloud.share.services.db.AsyncDBSessionFactory
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        // Core services
        val shareDao = ShareAsyncDao()
        val userClientFactory: (String) -> AuthenticatedClient = {
            RefreshingJWTAuthenticator(
                micro.client,
                it,
                micro.tokenValidation as TokenValidationJWT
            ).authenticateClient(OutgoingHttpCall)
        }

        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val shareService = ShareService(
            serviceClient = client,
            db = db,
            shareDao = shareDao,
            userClientFactory = userClientFactory,
            devMode = micro.developmentModeEnabled,
            eventStreamService = micro.eventStreamService
        )

        val processingService =
            ProcessingService(db, shareDao, userClientFactory, client, shareService)

        val shareQueryService = ShareQueryService(db, shareDao, client)

        if (micro.commandLineArguments.contains("--sync")) {
            val service = ShareSynchronization(db, shareDao, userClientFactory)
            runBlocking {
                service.synchronize()
            }
            exitProcess(0)
        }

        // Processors
        StorageEventProcessor(
            processingService,
            micro.eventStreamService,
            if (micro.developmentModeEnabled) "admin@dev" else "_share"
        ).init()
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
