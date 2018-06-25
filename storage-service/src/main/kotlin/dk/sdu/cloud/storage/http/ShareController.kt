package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FindByShareId
import dk.sdu.cloud.storage.api.ShareDescriptions
import dk.sdu.cloud.storage.api.ShareState
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.ShareService
import dk.sdu.cloud.storage.services.cephfs.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.tryWithShareService
import io.ktor.application.ApplicationCall
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class ShareController(
    private val shareService: ShareService,
    private val commandRunnerFactory: FSCommandRunnerFactory,
    private val fs: CoreFileSystemService
) {
    fun configure(routing: Route) = with(routing) {
        route("shares") {
            protect()

            implement(ShareDescriptions.list) {
                logEntry(log, it)
                tryWithShareService {
                    commandRunnerFactory.withContext(call.user) { ctx ->
                        ok(shareService.list(ctx, it.pagination))
                    }
                }
            }

            implement(ShareDescriptions.accept) {
                logEntry(log, it)

                tryWithShareService {
                    commandRunnerFactory.withContext(call.user) { ctx ->
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
                    commandRunnerFactory.withContext(call.user) { ctx ->
                        ok(shareService.update(ctx, it.id, it.rights))
                    }
                }
            }

            implement(ShareDescriptions.create) {
                logEntry(log, it)

                tryWithShareService {
                    commandRunnerFactory.withContext(call.user) { ctx ->
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