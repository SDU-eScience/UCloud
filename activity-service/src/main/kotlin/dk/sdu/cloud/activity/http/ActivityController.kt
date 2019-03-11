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
import io.ktor.application.call

class ActivityController<DBSession>(
    private val activityService: ActivityService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ActivityDescriptions.listByFileId) {
            val page = activityService.findEventsForFileId(request.normalize(), request.id)

            ok(page)
        }

        implement(ActivityDescriptions.listByPath) {
            with(ctx as HttpCall) {
                val page = activityService.findEventsForPath(
                    request.normalize(),
                    request.path,
                    call.request.bearer!!,
                    ctx.jobId
                )

                ok(page)
            }
        }

        implement(ActivityDescriptions.listByUser) {
            ok(activityService.findEventsForUser(request.normalize(), ctx.securityPrincipal.username))
        }

        implement(ActivityDescriptions.browseByUser) {

        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
