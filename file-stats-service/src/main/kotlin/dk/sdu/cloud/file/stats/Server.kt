package dk.sdu.cloud.file.stats

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.stats.http.FileStatsController
import dk.sdu.cloud.file.stats.services.DirectorySizeService
import dk.sdu.cloud.file.stats.services.RecentFilesService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val recentFilesService = RecentFilesService(client)
        val usageFileService = UsageService(client)
        val directorySizeService = DirectorySizeService(client)

        with(micro.server) {
            configureControllers(
                FileStatsController(recentFilesService, usageFileService, directorySizeService, client)
            )
        }

        startServices()
    }
}
