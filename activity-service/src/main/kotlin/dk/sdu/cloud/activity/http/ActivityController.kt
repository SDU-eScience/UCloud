package dk.sdu.cloud.activity.http

import dk.sdu.cloud.activity.api.ActivityDescriptions
import dk.sdu.cloud.activity.services.ActivityEventDao
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.routing.Route

class ActivityController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val activityEventDao: ActivityEventDao<DBSession>
) : Controller {
    override val baseContext: String = ActivityDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ActivityDescriptions.listByFileId) { req ->
            logEntry(log, req)

            val page = db.withTransaction {
                activityEventDao.findByFileId(it, req.normalize(), req.id)
            }

            ok(page)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}