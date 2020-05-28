package dk.sdu.cloud.news.api

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

data class PostMessageRequest(val title: String, val preamble: String, val message: String)
typealias PostMessageResponse = Unit

data class HideMessageRequest(val id: Int)
typealias HideMessageResponse = Unit

object News : CallDescriptionContainer("news") {
    val baseContext = "/api/news"

    val postMessage = call<PostMessageRequest, PostMessageResponse, CommonErrorMessage>("postMessage") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"post"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val hideMessage = call<HideMessageRequest, HideMessageResponse, CommonErrorMessage>("hideMessage") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.ADMIN
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"hide"

                body { bindEntireRequestFromBody() }
            }
        }
    }
}