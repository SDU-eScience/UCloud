package dk.sdu.cloud.slack.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

typealias SendMessageRequest = Alert
typealias SendMessageResponse = Unit

object SlackDescriptions : CallDescriptionContainer("slack") {
    val baseContext = "/api/slack"

    val sendMessage = call<SendMessageRequest, SendMessageResponse, CommonErrorMessage>("sendMessage") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"send"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
