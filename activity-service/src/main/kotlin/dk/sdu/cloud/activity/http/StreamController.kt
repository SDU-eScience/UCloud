package dk.sdu.cloud.activity.http

import dk.sdu.cloud.Roles
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

class StreamController<Session>(
    private val db: DBSessionFactory<Session>,
    private val activityService: ActivityService<Session>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ActivityDescriptions.streamByPath) {
            with(ctx as HttpCall) {
                db.withTransaction { session ->
                    ok(
                        activityService.findStreamForPath(
                            session,
                            request.normalize(),
                            request.path,
                            call.request.bearer!!,
                            ctx.jobId
                        )
                    )
                }
            }
        }

        implement(ActivityDescriptions.streamForUser) {
            val user =
                request.user?.takeIf { ctx.securityPrincipal.role in Roles.ADMIN } ?: ctx.securityPrincipal.username

            db.withTransaction { session ->
                ok(activityService.findStreamForUser(session, request.normalize(), user))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
