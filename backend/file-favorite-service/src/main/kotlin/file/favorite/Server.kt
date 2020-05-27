package dk.sdu.cloud.file.favorite

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.favorite.http.FileFavoriteController
import dk.sdu.cloud.file.favorite.migration.MetadataMigration
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.commandLineArguments
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val fileFavoriteService = FileFavoriteService(client)

        if (micro.commandLineArguments.contains("--migrate-metadata")) {
            runBlocking<Nothing> {
                try {
                    MetadataMigration(micro, client).runDataMigration()
                } catch (ex: Throwable) {
                    log.error(ex.stackTraceToString())
                    exitProcess(1)
                }
                exitProcess(0)
            }
        }

        with(micro.server) {
            configureControllers(
                FileFavoriteController(fileFavoriteService, client.withoutAuthentication())
            )
        }

        startServices()
    }
}
