package dk.sdu.cloud.slack.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.builtins.serializer

typealias SendAlertRequest = Alert
typealias SendAlertResponse = Unit

typealias SendSupportRequest = Ticket
typealias SendSupportResponse = Unit

object SlackDescriptions : CallDescriptionContainer("slack") {
    private const val baseContext = "/api/slack"

    init {
        description = """
            The slack-service is used for sending alerts and other notifications to a Slack channel used by admins of the service.
        """.trimIndent()
    }

    val sendAlert = call("sendAlert", SendAlertRequest.serializer(), SendAlertResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"sendAlert"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Sends an alert"
        }
    }

    val sendSupport = call("sendSupport", SendSupportRequest.serializer(), SendSupportResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"sendSupport"
            }

            body { bindEntireRequestFromBody() }
        }


        documentation {
            summary = "Sends a support message"
        }
    }
}
