package dk.sdu.cloud.file.favorite.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteFileAudit
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteRequest
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteResponse
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call

class FileFavoriteController<DBSession>(
    private val fileFavoriteService: FileFavoriteService<DBSession>,
    private val cloudContext: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        handleFavoriteToggle(FileFavoriteDescriptions.toggleFavorite)
        handleFavoriteToggle(FileFavoriteDescriptions.toggleFavoriteDelete)

        implement(FileFavoriteDescriptions.favoriteStatus) {
            ok(
                FavoriteStatusResponse(
                    fileFavoriteService.getFavoriteStatus(request.files, ctx.securityPrincipal.username)
                )
            )
        }

        implement(FileFavoriteDescriptions.list) {
            ok(fileFavoriteService.listAll(request.normalize(), ctx.securityPrincipal.username))
        }
    }

    private fun RpcServer.handleFavoriteToggle(
        description: CallDescription<ToggleFavoriteRequest, ToggleFavoriteResponse, CommonErrorMessage>
    ) {
        implement(description) {
            with(ctx as HttpCall) {
                val auditMessage = ToggleFavoriteAudit(listOf(request.path).map { ToggleFavoriteFileAudit(it) })
                audit(auditMessage)

                ok(
                    ToggleFavoriteResponse(
                        fileFavoriteService.toggleFavorite(
                            listOf(request.path),
                            ctx.securityPrincipal.username,
                            cloudContext.withoutAuthentication().bearerAuth(call.request.bearer!!),
                            auditMessage
                        )
                    )
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
