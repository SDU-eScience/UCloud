package dk.sdu.cloud.activity.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.activity.api.Activity
import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ActivityController(
    private val activityService: ActivityService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ActivityDescriptions.listByPath) {
            with(ctx as HttpCall) {
                val page = activityService.findEventsForPath(
                    request.normalize(),
                    request.path,
                    ctx.bearer!!,
                    ctx.securityPrincipal.username,
                    ctx.jobId
                )

                ok(page)
            }
        }

        implement(ActivityDescriptions.activityFeed) {
            val user = if (ctx.project == null) {
                request.user?.takeIf { ctx.securityPrincipal.role in Roles.PRIVILEGED }
                    ?: ctx.securityPrincipal.username
            } else {
                ctx.securityPrincipal.username
            }

            val result = activityService.browseActivity(request.normalize(), user, request, ctx.project)
            ok(Activity.BrowseByUser.Response(result.endOfScroll, result.items, result.nextOffset))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
