package dk.sdu.cloud.slack.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*

typealias SendAlertRequest = Alert
typealias SendAlertResponse = Unit

typealias SendSupportRequest = Ticket
typealias SendSupportResponse = Unit

object SlackDescriptions : CallDescriptionContainer("slack") {
    val baseContext = "/api/slack"

    val sendAlert = call<SendAlertRequest, SendAlertResponse, CommonErrorMessage>("sendAlert") {
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
    }

    val sendSupport = call<SendSupportRequest, SendSupportResponse, CommonErrorMessage>("sendSupport") {
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
    }
}
