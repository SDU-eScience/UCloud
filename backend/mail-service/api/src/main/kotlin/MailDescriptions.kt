package dk.sdu.cloud.mail.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class SendRequest(
    val userId: String,
    val subject: String,
    val message: String,
    val mandatory: Boolean? = false
)

data class SendBulkRequest(
    val messages: List<SendRequest>
)

object MailDescriptions : CallDescriptionContainer("mail") {
    val baseContext = "/api/mail"

    val send = call<SendRequest, Unit, CommonErrorMessage>("send") {
        auth {
            roles = setOf(Role.SERVICE)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val sendBulk = call<SendBulkRequest, Unit, CommonErrorMessage>("sendBulk") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
