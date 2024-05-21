package dk.sdu.cloud.notification.http

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.notification.api.*
import dk.sdu.cloud.notification.services.NotificationService
import dk.sdu.cloud.notification.services.SubscriptionService
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.actorAndProject
import kotlinx.coroutines.delay

class NotificationController(
    private val service: NotificationService,
    private val subscriptionService: SubscriptionService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(NotificationDescriptions.list) {
            ok(
                service.listNotifications(
                    ctx.securityPrincipal.username,
                    request.type,
                    request.since,
                    request.normalize()
                )
            )
        }

        implement(NotificationDescriptions.markAsRead) {
            ok(
                MarkResponse(
                    service.markAsRead(
                        ctx.securityPrincipal.username,
                        request.normalizedIds
                    )
                )
            )
        }

        implement(NotificationDescriptions.markAllAsRead) {
            service.markAllAsRead(ctx.securityPrincipal.username)
            ok(Unit)
        }

        implement(NotificationDescriptions.create) {
            ok(service.createNotification(request.user, request.notification))
        }

        implement(NotificationDescriptions.createBulk) {
            ok(BulkResponse(service.createNotifications(request.items)))
        }

        implement(NotificationDescriptions.delete) {
            ok(DeleteResponse(service.deleteNotifications(request.normalizedIds)))
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

        implement(NotificationDescriptions.updateSettings)  {
            service.updateSettings(actorAndProject.actor.safeUsername(), request)
            ok(Unit)
        }

        implement(NotificationDescriptions.retrieveSettings) {
            ok(RetrieveSettingsResponse(service.retrieveSettings(actorAndProject.actor.safeUsername())))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
