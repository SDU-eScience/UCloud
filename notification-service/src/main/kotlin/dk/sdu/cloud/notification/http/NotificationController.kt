package dk.sdu.cloud.notification.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.services.NotificationDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class NotificationController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: NotificationDAO<DBSession>
) : Controller {
    override val baseContext = NotificationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(NotificationDescriptions.list) { req ->
            logEntry(log, req)
            ok(
                db.withTransaction {
                    source.findNotifications(it, call.securityPrincipal.username, req.type, req.since, req.pagination)
                }
            )
        }

        implement(NotificationDescriptions.markAsRead) { req ->
            logEntry(log, req)
            val success = db.withTransaction { source.markAsRead(it, call.securityPrincipal.username, req.id) }
            if (success) ok(Unit) else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
        }

        implement(NotificationDescriptions.create) { req ->
            logEntry(log, req)
            ok(db.withTransaction { FindByNotificationId(source.create(it, req.user, req.notification)) })
        }

        implement(NotificationDescriptions.delete) { req ->
            logEntry(log, req)
            val success = db.withTransaction { source.delete(it, req.id) }
            if (success) ok(Unit) else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationController::class.java)
    }
}
