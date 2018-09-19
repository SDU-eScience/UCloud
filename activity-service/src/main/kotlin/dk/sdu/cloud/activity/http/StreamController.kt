package dk.sdu.cloud.activity.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.routing.Route

class StreamController<Session>(
    private val db: DBSessionFactory<Session>,
    private val activityService: ActivityService<Session>
) : Controller {
    override val baseContext = ActivityDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ActivityDescriptions.streamByPath) { req ->
            logEntry(log, req)

            db.withTransaction { session ->
                ok(
                    activityService.findStreamForPath(
                        session,
                        req.normalize(),
                        req.path,
                        call.request.bearer!!,
                        call.request.jobId
                    )
                )
            }
        }

        implement(ActivityDescriptions.streamForUser) { req ->
            logEntry(log, req)

            val user =
                req.user?.takeIf { call.securityPrincipal.role in Roles.ADMIN } ?: call.securityPrincipal.username

            db.withTransaction { session ->
                ok(activityService.findStreamForUser(session, req.normalize(), user))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}