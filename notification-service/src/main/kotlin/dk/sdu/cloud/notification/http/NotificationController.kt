package dk.sdu.cloud.notification.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.notification.api.DeleteResponse
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.MarkResponse
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.services.NotificationDAO
import dk.sdu.cloud.notification.services.SubscriptionService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger

class NotificationController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val source: NotificationDAO<DBSession>,
    private val subscriptionService: SubscriptionService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(NotificationDescriptions.list) {
            ok(
                db.withTransaction {
                    source.findNotifications(
                        it,
                        ctx.securityPrincipal.username,
                        request.type,
                        request.since,
                        request.pagination
                    )
                }
            )
        }

        implement(NotificationDescriptions.markAsRead) {
            // TODO Optimize this
            val failedMarkings = mutableListOf<Long>()
            val isTrue: (Boolean) -> Boolean = { it }
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
            val result =
                db.withTransaction { FindByNotificationId(source.create(it, request.user, request.notification)) }

            GlobalScope.launch {
                subscriptionService.onNotification(
                    request.user,
                    request.notification.copy(id = result.id),
                    allowRemoteCalls = true
                )
            }

            ok(result)
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

        implement(NotificationDescriptions.subscription) {
            val username = ctx.securityPrincipal.username
            withContext<WSCall> {
                ctx.session.addOnCloseHandler {
                    subscriptionService.onDisconnect(ctx.session)
                }
            }

            subscriptionService.onConnection(username, this@implement)

            while (true) {
                delay(1000)
            }
        }

        implement(NotificationDescriptions.internalNotification) {
            subscriptionService.onNotification(request.user, request.notification, allowRemoteCalls = false)
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
