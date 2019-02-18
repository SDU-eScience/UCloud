package dk.sdu.cloud.activity.http

import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.call

class ActivityController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val activityService: ActivityService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ActivityDescriptions.listByFileId) {
            val page = db.withTransaction {
                activityService.findEventsForFileId(it, request.normalize(), request.id)
            }

            ok(page)
        }

        implement(ActivityDescriptions.listByPath) {
            with(ctx as HttpCall) {
                val page = db.withTransaction {
                    activityService.findEventsForPath(
                        it,
                        request.normalize(),
                        request.path,
                        call.request.bearer!!,
                        ctx.jobId
                    )
                }

                ok(page)
            }
        }

        implement(ActivityDescriptions.listByUser) {
            ok(
                db.withTransaction {
                    activityService.findEventsForUser(it, request.normalize(), ctx.securityPrincipal.username)
                }
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
