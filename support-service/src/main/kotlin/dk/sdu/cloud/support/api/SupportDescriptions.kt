package dk.sdu.cloud.support.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class CreateTicketRequest(
    val message: String
)

object SupportDescriptions : CallDescriptionContainer("support") {
    val baseContext = "/api/support"

    val createTicket = call<CreateTicketRequest, Unit, CommonErrorMessage>("createTicket") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"ticket"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
