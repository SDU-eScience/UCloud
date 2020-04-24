package dk.sdu.cloud.file.stats.http

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.stats.api.DirectorySizesResponse
import dk.sdu.cloud.file.stats.api.FileStatsDescriptions
import dk.sdu.cloud.file.stats.api.UsageResponse
import dk.sdu.cloud.file.stats.services.DirectorySizeService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class FileStatsController(
    private val usageService: UsageService,
    private val directorySizeService: DirectorySizeService,
    private val cloud: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileStatsDescriptions.usage) {
            val path = request.path ?: homeDirectory(ctx.securityPrincipal.username)

            ok(UsageResponse(usageService.calculateUsage(path, ctx.securityPrincipal.username), path))
        }

        implement(FileStatsDescriptions.directorySize) {
            val paths = request.paths.toList()
            ok(
                DirectorySizesResponse(
                    directorySizeService.fetchDirectorySizes(paths, ctx.securityPrincipal.username)
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
