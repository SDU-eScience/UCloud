package dk.sdu.cloud.file.stats.http

import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.stats.api.FileStatsDescriptions
import dk.sdu.cloud.file.stats.api.RecentFilesResponse
import dk.sdu.cloud.file.stats.api.UsageResponse
import dk.sdu.cloud.file.stats.services.RecentFilesService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.ok
import dk.sdu.cloud.service.safeJobId
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class FileStatsController(
    private val recentFilesService: RecentFilesService,
    private val usageService: UsageService
) : Controller {
    override val baseContext = FileStatsDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileStatsDescriptions.usage) { req ->
            val path = req.path ?: homeDirectory(call.securityPrincipal.username)
            ok(
                UsageResponse(
                    usageService.calculateUsage(
                        path,
                        call.securityPrincipal.username,
                        call.request.safeJobId
                    ),
                    path
                )
            )
        }

        implement(FileStatsDescriptions.recent) {
            ok(
                RecentFilesResponse(
                    recentFilesService.queryRecentFiles(call.securityPrincipal.username, call.request.safeJobId)
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
