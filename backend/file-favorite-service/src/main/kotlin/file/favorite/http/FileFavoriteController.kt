package dk.sdu.cloud.file.favorite.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteFileAudit
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteRequest
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteResponse
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class FileFavoriteController(
    private val fileFavoriteService: FileFavoriteService,
    private val clientContext: ClientAndBackend
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        handleFavoriteToggle(FileFavoriteDescriptions.toggleFavorite)

        implement(FileFavoriteDescriptions.favoriteStatus) {
            ok(
                FavoriteStatusResponse(
                    fileFavoriteService.getFavoriteStatus(request.files, ctx.securityToken)
                )
            )
        }

        implement(FileFavoriteDescriptions.list) {
            ok(
                fileFavoriteService.listAll(
                    request.normalize(), ctx.securityToken,
                    clientContext.bearerAuth(ctx.bearer!!)
                )
            )
        }
    }

    private fun RpcServer.handleFavoriteToggle(
        description: CallDescription<ToggleFavoriteRequest, ToggleFavoriteResponse, CommonErrorMessage>
    ) {
        implement(description) {
            val auditMessage = ToggleFavoriteAudit(listOf(request.path).map { ToggleFavoriteFileAudit(it) })
            audit(auditMessage)

            ok(
                ToggleFavoriteResponse(
                    fileFavoriteService.toggleFavorite(
                        listOf(request.path),
                        ctx.securityToken,
                        clientContext.bearerAuth(ctx.bearer!!)
                    )
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
