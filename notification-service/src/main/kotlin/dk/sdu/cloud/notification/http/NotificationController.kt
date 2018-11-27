package dk.sdu.cloud.notification.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.notification.api.DeleteResponse
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.MarkResponse
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.services.NotificationDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.Logger

class NotificationController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: NotificationDAO<DBSession>
) : Controller {
    override val baseContext = NotificationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(NotificationDescriptions.list) { req ->
            ok(
                db.withTransaction {
                    source.findNotifications(it, call.securityPrincipal.username, req.type, req.since, req.pagination)
                }
            )
        }

        implement(NotificationDescriptions.markAsRead) { req ->
            // TODO Optimize this
            val failedMarkings = mutableListOf<Long>()
            val isTrue: (Boolean) -> Boolean = { it == true }
            val success = db.withTransaction { session ->
                val user = call.securityPrincipal.username
                req.bulkId.id.map { id ->
                    val accepted = source.markAsRead(session, user, id)
                    if (!accepted) failedMarkings.add(id)
                    accepted
                }.any(isTrue)

            }

            if (success) ok(MarkResponse(failedMarkings.toList()))
            else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
        }

        implement(NotificationDescriptions.markAllAsRead) { req ->
            db.withTransaction {
                source.markAllAsRead(it, call.securityPrincipal.username)
            }

            ok(Unit)
        }

        implement(NotificationDescriptions.create) { req ->
            ok(db.withTransaction { FindByNotificationId(source.create(it, req.user, req.notification)) })
        }

        implement(NotificationDescriptions.delete) { req ->
            val failedDeletions = mutableListOf<Long>()
            val isTrue: (Boolean) -> Boolean = { it == true }
            val success = db.withTransaction { session ->
                req.bulkId.id.map { id ->
                    val deleted = source.delete(session, id)
                    if (!deleted) failedDeletions.add(id)
                    deleted
                }.any(isTrue)
            }

            if (success) ok(DeleteResponse(failedDeletions.toList()))
            else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
