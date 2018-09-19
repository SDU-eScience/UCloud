package dk.sdu.cloud.activity.http

import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.routing.Route

class ActivityController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val activityService: ActivityService<DBSession>
) : Controller {
    override val baseContext: String = ActivityDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ActivityDescriptions.listByFileId) { req ->
            logEntry(log, req)

            val page = db.withTransaction {
                activityService.findEventsForFileId(it, req.normalize(), req.id)
            }

            ok(page)
        }

        implement(ActivityDescriptions.listByPath) { req ->
            logEntry(log, req)
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