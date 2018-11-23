package dk.sdu.cloud.share.http

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.bearerAuth
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.share.api.FindByShareId
import dk.sdu.cloud.share.api.ShareDescriptions
import dk.sdu.cloud.share.services.ShareService
import io.ktor.routing.Route

class ShareController(
    private val shareService: ShareService<*>,
    private val cloudContext: CloudContext
) : Controller {
    override val baseContext = ShareDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ShareDescriptions.create) { req ->
            val id = shareService.create(
                call.securityPrincipal.username,
                req,
                cloudContext.bearerAuth(call.request.bearer!!)
            )

            ok(FindByShareId(id))
        }

        implement(ShareDescriptions.accept) { req ->
            shareService.acceptShare(
                call.securityPrincipal.username,
                req.id,
                cloudContext.bearerAuth(call.request.bearer!!)
            )

            ok(Unit)
        }

        implement(ShareDescriptions.revoke) { req ->
            shareService.deleteShare(
                call.securityPrincipal.username,
                req.id
            )

            ok(Unit)
        }

        implement(ShareDescriptions.update) { req ->
            shareService.updateRights(
                call.securityPrincipal.username,
                req.id,
                req.rights
            )

            ok(Unit)
        }

        implement(ShareDescriptions.list) { req ->
            ok(
                shareService.list(
                    call.securityPrincipal.username,
                    req.state,
                    req.normalize()
                )
            )
        }

        implement(ShareDescriptions.findByPath) { req ->
            ok(
                shareService.findSharesForPath(
                    call.securityPrincipal.username,
                    req.path
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
