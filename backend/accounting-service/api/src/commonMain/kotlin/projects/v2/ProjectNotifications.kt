package dk.sdu.cloud.project.api.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiDoc(
    """
        A notification which indicates that a Project has changed

        The notification contains the _current_ state of the `Project`. The project may have changed since the
        notification was originally created. If multiple updates occur to a `Project` before a provider invokes
        `markAsRead` then only a single notification will be created by UCloud.
    """
)
data class ProjectNotification(
    @UCloudApiDoc(
        """
            An identifier which uniquely identifies this notifications

            The identifier is never re-used for a new notifications.
        """
    )
    val id: String,

    @UCloudApiDoc("The current state of the project which has been updated")
    val project: Project
)

object ProjectNotifications : CallDescriptionContainer("projects.v2.notifications") {
    const val baseContext = "/api/projects/v2/notifications"

    val retrieve = call<Unit, BulkResponse<ProjectNotification>, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)

        documentation {
            summary = "Pulls the database for more `ProjectNotification`s"

            description = """
                This request fetches a new batch of `ProjectNotification`s. The provider should aim to handle all
                notifications as soon as possible. Once a notification has been handled, the provider should call
                `ProjectNotifications.markAsRead` with the appropriate `id`s. A robust provider implementation should
                be able to handle receiving the same notification twice.
                
                It is recommended that a provider calls this endpoint immediately after starting.
            """.trimIndent()
        }
    }

    val markAsRead = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("markAsRead") {
        httpUpdate(baseContext, "markAsRead", roles = Roles.PROVIDER)

        documentation {
            summary = "Marks one or more `ProjectNotification` as read"
        }
    }
}

open class ProjectNotificationsProvider(
    provider: String
) : CallDescriptionContainer("projects.v2.notifications.provider.$provider") {
    val baseContext = "/ucloud/$provider/projects/v2/notifications"

    val pullRequest = call<Unit, Unit, CommonErrorMessage>("pullRequest") {
        httpUpdate(baseContext, "pullRequest", roles = Roles.SERVICE)

        documentation {
            summary = "Request from UCloud that the provider pulls for more notifications"
            description = """
                The provider is supposed to call `ProjectNotifications.retrieve` as soon as possible after receiving
                this call. A 200 OK response can be sent immediately to this request, without dealing with any
                notifications.
            """.trimIndent()
        }
    }
}

