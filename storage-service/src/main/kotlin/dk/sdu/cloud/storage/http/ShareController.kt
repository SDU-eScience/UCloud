package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FindByShareId
import dk.sdu.cloud.storage.api.ShareDescriptions
import dk.sdu.cloud.storage.api.ShareState
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.tryWithFS
import io.ktor.application.ApplicationCall
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class ShareController<Ctx : FSUserContext>(
    private val shareService: ShareService<*, Ctx>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>
) {
    fun configure(routing: Route) = with(routing) {
        route("shares") {
            protect()

            implement(ShareDescriptions.list) {
                logEntry(log, it)
                tryWithFS(commandRunnerFactory, call.user) { ctx ->
                    tryWithShareService {
                        ok(shareService.list(ctx, it.pagination))
                    }
                }
            }

            implement(ShareDescriptions.accept) {
                logEntry(log, it)

                tryWithShareService {
                    tryWithFS(commandRunnerFactory, call.user) { ctx ->
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
                    ok(shareService.deleteShare(call.user, it.id))
                }
            }

            implement(ShareDescriptions.reject) {
                logEntry(log, it)

                tryWithShareService {
                    ok(shareService.deleteShare(call.user, it.id))
                }
            }

            implement(ShareDescriptions.update) {
                logEntry(log, it)

                tryWithShareService {
                    tryWithFS(commandRunnerFactory, call.user) { ctx ->
                        ok(shareService.updateRights(ctx, it.id, it.rights))
                    }
                }
            }

            implement(ShareDescriptions.create) {
                logEntry(log, it)

                tryWithShareService {
                    tryWithFS(commandRunnerFactory, call.user) { ctx ->
                        ok(FindByShareId(shareService.create(ctx, it, call.cloudClient)))
                    }
                }
            }
        }
    }

    val ApplicationCall.user: String get() = request.validatedPrincipal.subject

    companion object {
        private val log = LoggerFactory.getLogger(ShareController::class.java)
    }
}