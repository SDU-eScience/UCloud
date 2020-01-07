package dk.sdu.cloud.pong.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.websocket
import io.ktor.http.HttpMethod

data class Message(val request: String)

typealias SubscriptionRequest = Message
typealias SubscriptionResponse = SubscriptionRequest

typealias RegularCallRequest = SubscriptionRequest
typealias RegularCallResponse = SubscriptionRequest

object Pongs : CallDescriptionContainer("pong") {
    val baseContext = "/api/pong"

    val subscription = call<SubscriptionRequest, SubscriptionResponse, CommonErrorMessage>("subscription") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        websocket(baseContext)
    }

    val regularCall = call<RegularCallRequest, RegularCallResponse, CommonErrorMessage>("regularCall") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        websocket(baseContext)

        http {
            method = HttpMethod.Post

            path { using(baseContext) }

            body { bindEntireRequestFromBody() }
        }
    }
}
