package dk.sdu.cloud.notification.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.notification.api.DeleteResponse
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.MarkResponse
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.services.NotificationDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.Logger

class NotificationController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: NotificationDAO<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(NotificationDescriptions.list) {
            ok(
                db.withTransaction {
                    source.findNotifications(it, ctx.securityPrincipal.username, request.type, request.since, request.pagination)
                }
            )
        }

        implement(NotificationDescriptions.markAsRead) {
            // TODO Optimize this
            val failedMarkings = mutableListOf<Long>()
            val isTrue: (Boolean) -> Boolean = { it == true }
            val success = db.withTransaction { session ->
                val user = ctx.securityPrincipal.username
                request.bulkId.ids.map { id ->
                    val accepted = source.markAsRead(session, user, id)
                    if (!accepted) failedMarkings.add(id)
                    accepted
                }.any(isTrue)

            }

            if (success) ok(MarkResponse(failedMarkings.toList()))
            else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
        }

        implement(NotificationDescriptions.markAllAsRead) {
            db.withTransaction {
                source.markAllAsRead(it, ctx.securityPrincipal.username)
            }

            ok(Unit)
        }

        implement(NotificationDescriptions.create) {
            ok(db.withTransaction { FindByNotificationId(source.create(it, request.user, request.notification)) })
        }

        implement(NotificationDescriptions.delete) {
            val failedDeletions = mutableListOf<Long>()
            val isTrue: (Boolean) -> Boolean = { it }
            val success = db.withTransaction { session ->
                request.bulkId.ids.map { id ->
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
