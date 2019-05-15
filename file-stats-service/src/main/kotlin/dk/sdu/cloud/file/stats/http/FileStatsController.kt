package dk.sdu.cloud.file.stats.http

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.stats.api.DirectorySizesResponse
import dk.sdu.cloud.file.stats.api.FileStatsDescriptions
import dk.sdu.cloud.file.stats.api.RecentFilesResponse
import dk.sdu.cloud.file.stats.api.UsageResponse
import dk.sdu.cloud.file.stats.services.DirectorySizeService
import dk.sdu.cloud.file.stats.services.RecentFilesService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class FileStatsController(
    private val recentFilesService: RecentFilesService,
    private val usageService: UsageService,
    private val directorySizeService: DirectorySizeService,
    private val cloud: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileStatsDescriptions.usage) {
            val path = request.path ?: FileDescriptions.findHomeFolder.call(
                FindHomeFolderRequest(
                    ctx.securityPrincipal.username
                ),
                cloud
            ).orThrow().path
            ok(
                UsageResponse(
                    usageService.calculateUsage(
                        path,
                        ctx.securityPrincipal.username,
                        ctx.jobId
                    ),
                    path
                )
            )
        }

        implement(FileStatsDescriptions.recent) {
            ok(
                RecentFilesResponse(
                    recentFilesService.queryRecentFiles(ctx.securityPrincipal.username, ctx.jobId)
                )
            )
        }

        implement(FileStatsDescriptions.directorySize) {
            val paths = request.fileIds.toList()
            ok(
                DirectorySizesResponse(
                    directorySizeService.fetchDirectorySizes(paths, ctx.securityPrincipal.username, ctx.jobId)
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
