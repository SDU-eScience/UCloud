package dk.sdu.cloud.share.http

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.share.api.FindByShareId
import dk.sdu.cloud.share.api.ShareDescriptions
import dk.sdu.cloud.share.services.ShareService
import io.ktor.application.call

class ShareController(
    private val shareService: ShareService<*>,
    private val serviceClient: AuthenticatedClient
) : Controller {
    private val clientAndBackend = serviceClient.withoutAuthentication()

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ShareDescriptions.create) {
            val bearer = (ctx as HttpCall).call.request.bearer!!
            val id = shareService.create(
                ctx.securityPrincipal.username,
                request,
                bearer,
                clientAndBackend.bearerAuth(bearer)
            )

            ok(FindByShareId(id))
        }

        implement(ShareDescriptions.accept) {
            val bearer = (ctx as HttpCall).call.request.bearer!!
            shareService.acceptShare(
                ctx.securityPrincipal.username,
                request.id,
                bearer,
                clientAndBackend.bearerAuth(bearer),
                request.createLink ?: true
            )

            ok(Unit)
        }

        implement(ShareDescriptions.revoke) {
            shareService.deleteShare(
                ctx.securityPrincipal.username,
                request.id
            )

            ok(Unit)
        }

        implement(ShareDescriptions.update) {
            shareService.updateRights(
                ctx.securityPrincipal.username,
                request.id,
                request.rights
            )

            ok(Unit)
        }

        implement(ShareDescriptions.list) {
            ok(
                shareService.list(
                    ctx.securityPrincipal.username,
                    request.state,
                    request.normalize()
                )
            )
        }

        implement(ShareDescriptions.findByPath) {
            ok(
                shareService.findSharesForPath(
                    ctx.securityPrincipal.username,
                    request.path
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
