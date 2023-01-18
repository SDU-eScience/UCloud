package dk.sdu.cloud.project.api.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiDoc(
    """
        A notification which indicates that a Project has changed

        The notification contains the _current_ state of the `Project`. The project may have changed since the
        notification was originally created. If multiple updates occur to a `Project` before a provider invokes
        `markAsRead` then only a single notification will be created by UCloud.
    """
)
@UCloudApiStable
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

@UCloudApiStable
object ProjectNotifications : CallDescriptionContainer("projects.v2.notifications") {
    const val baseContext = "/api/projects/v2/notifications"

    init {
        description = """
            Project notifications are used by providers to synchronize UCloud's state with the provider's local state.
            
            This feature is, for example, used to synchronize unix groups on a traditional HPC system with UCloud's
            project structure. A provider will receive notifications for all relevant projects. A project is considered
            relevant if the project has been allocated some resource on the provider. Providers should be aware that
            they will receive notifications about users even if the users haven't connected yet. This problem is
            typically handled by the integration module.
        """.trimIndent()
    }


    val retrieve = call("retrieve", Unit.serializer(), BulkResponse.serializer(ProjectNotification.serializer()), CommonErrorMessage.serializer()) {
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

    val markAsRead = call("markAsRead", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "markAsRead", roles = Roles.PROVIDER)

        documentation {
            summary = "Marks one or more `ProjectNotification` as read"
        }
    }
}

@UCloudApiStable
open class ProjectNotificationsProvider(
    provider: String
) : CallDescriptionContainer("projects.v2.notifications.provider.$provider") {
    val baseContext = "/ucloud/$provider/projects/v2/notifications"

    init {
        description = """
            The provider API to be notified about project notifications.
        """.trimIndent()
    }

    val pullRequest = call("pullRequest", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
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

