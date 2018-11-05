package dk.sdu.cloud.storage.http

import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.share.api.FindByShareId
import dk.sdu.cloud.share.api.ShareDescriptions
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.ShareService
import dk.sdu.cloud.storage.util.tryWithFS
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class ShareController<Ctx : FSUserContext>(
    private val shareService: ShareService<*, Ctx>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
) : Controller {
    override val baseContext = ShareDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ShareDescriptions.list) {
            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                ok(shareService.list(ctx, it.normalize()))
            }
        }

        implement(ShareDescriptions.accept) {
             tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                 ok(
                     shareService.updateState(
                         ctx,
                         it.id,
                         ShareState.ACCEPTED
                     )
                 )
             }
        }

        implement(ShareDescriptions.revoke) {
            ok(shareService.deleteShare(call.securityPrincipal.username, it.id))
        }

        implement(ShareDescriptions.reject) {
            ok(shareService.deleteShare(call.securityPrincipal.username, it.id))
        }

        implement(ShareDescriptions.update) {
            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                ok(shareService.updateRights(ctx, it.id, it.rights))
            }
        }

        implement(ShareDescriptions.create) {
            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                ok(FindByShareId(shareService.create(ctx, it, call.cloudClient)))
            }
        }

        implement(ShareDescriptions.findByPath) {
            ok(shareService.findSharesForPath(call.securityPrincipal.username, it.path))


        }

        implement(ShareDescriptions.listByStatus) {
            ok(shareService.listSharesByStatus(call.securityPrincipal.username, it.status, it.normalize()))

        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareController::class.java)
    }
}
