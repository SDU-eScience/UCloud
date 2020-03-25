package dk.sdu.cloud.messages.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class PostMessageRequest(val message: String)
typealias PostMessageResponse = Unit

object MessagesDescriptions : CallDescriptionContainer("messages") {
    val baseContext = "/api/messages"

    val postMessage = call<PostMessageRequest, PostMessageResponse, CommonErrorMessage>("example") {
        auth {
            access = AccessRight.READ

        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
