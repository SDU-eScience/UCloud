package dk.cloud.sdu.notification.http

import dk.cloud.sdu.notification.api.FindByNotificationId
import dk.cloud.sdu.notification.api.NotificationDescriptions
import dk.cloud.sdu.notification.services.NotificationDAO
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class NotificationController(private val source: NotificationDAO) {
    fun configure(routing: Route) = with(routing) {
        route("notifications") {
            protect()

            implement(NotificationDescriptions.list) {
                logEntry(log, it)
                ok(source.findNotifications(call.request.validatedPrincipal.subject, it.type, it.since, it.pagination))
            }

            implement(NotificationDescriptions.markAsRead) {
                logEntry(log, it)
                val success = source.markAsRead(call.request.validatedPrincipal.subject, it.id)
                if (success) ok(Unit) else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            }

            implement(NotificationDescriptions.create) {
                logEntry(log, it)
                if (!protect(rolesAllowed = listOf(Role.SERVICE, Role.ADMIN))) return@implement
                ok(FindByNotificationId(source.create(it.user, it.notification)))
            }

            implement(NotificationDescriptions.delete) {
                logEntry(log, it)
                if (!protect(rolesAllowed = listOf(Role.SERVICE, Role.ADMIN))) return@implement
                val success = source.delete(it.id)
                if (success) ok(Unit) else error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationController::class.java)
    }
}