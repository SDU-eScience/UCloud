package dk.sdu.cloud.share

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.share.http.ShareController
import dk.sdu.cloud.share.migration.MetadataMigration
import dk.sdu.cloud.share.services.ShareQueryService
import dk.sdu.cloud.share.services.ShareService
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        // Core services
        val userClientFactory: (String) -> AuthenticatedClient = {
            RefreshingJWTAuthenticator(
                micro.client,
                it,
                micro.tokenValidation as TokenValidationJWT
            ).authenticateClient(OutgoingHttpCall)
        }

        val shareService = ShareService(
            serviceClient = client,
            userClientFactory = userClientFactory,
            devMode = micro.developmentModeEnabled
        )

        val shareQueryService = ShareQueryService(client)

        if (micro.commandLineArguments.contains("--migrate-metadata")) {
            runBlocking<Nothing> {
                try {
                    MetadataMigration(AsyncDBSessionFactory(micro.databaseConfig), client).runDataMigration()
                } catch (ex: Throwable) {
                    log.error(ex.stackTraceToString())
                    exitProcess(1)
                }

                exitProcess(0)
            }
        }

        // Controllers
        with(micro.server) {
            configureControllers(
                ShareController(shareService, shareQueryService, client)
            )
        }

        startServices()
    }
}
