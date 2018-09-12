package dk.sdu.cloud.storage.http

import dk.sdu.cloud.service.*
import dk.sdu.cloud.share.api.FindByShareId
import dk.sdu.cloud.share.api.ShareDescriptions
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.tryWithFS
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class ShareController<Ctx : FSUserContext>(
    private val shareService: ShareService<*, Ctx>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
): Controller {
    override val baseContext = ShareDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ShareDescriptions.list) {
            logEntry(log, it)
            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                tryWithShareService {
                    ok(shareService.list(ctx, it.normalize()))
                }
            }
        }

        implement(ShareDescriptions.accept) {
            logEntry(log, it)

            tryWithShareService {
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
        }

        implement(ShareDescriptions.revoke) {
            logEntry(log, it)

            tryWithShareService {
                ok(shareService.deleteShare(call.securityPrincipal.username, it.id))
            }
        }

        implement(ShareDescriptions.reject) {
            logEntry(log, it)

            tryWithShareService {
                ok(shareService.deleteShare(call.securityPrincipal.username, it.id))
            }
        }

        implement(ShareDescriptions.update) {
            logEntry(log, it)

            tryWithShareService {
                tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                    ok(shareService.updateRights(ctx, it.id, it.rights))
                }
            }
        }

        implement(ShareDescriptions.create) {
            logEntry(log, it)

            tryWithShareService {
                tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                    ok(FindByShareId(shareService.create(ctx, it, call.cloudClient)))
                }
            }
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareController::class.java)
    }
}