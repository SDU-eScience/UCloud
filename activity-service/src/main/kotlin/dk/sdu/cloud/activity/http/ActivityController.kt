package dk.sdu.cloud.activity.http

import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.jobId
import io.ktor.routing.Route

class ActivityController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val activityService: ActivityService<DBSession>
) : Controller {
    override val baseContext: String = ActivityDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ActivityDescriptions.listByFileId) { req ->
            val page = db.withTransaction {
                activityService.findEventsForFileId(it, req.normalize(), req.id)
            }

            ok(page)
        }

        implement(ActivityDescriptions.listByPath) { req ->
            val page = db.withTransaction {
                activityService.findEventsForPath(
                    it,
                    req.normalize(),
                    req.path,
                    call.request.bearer!!,
                    call.request.jobId
                )
            }

            ok(page)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
