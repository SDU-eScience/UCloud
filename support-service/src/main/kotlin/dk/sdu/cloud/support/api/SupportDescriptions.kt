package dk.sdu.cloud.support.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class CreateTicketRequest(
    val message: String
)

object SupportDescriptions : RESTDescriptions("support") {
    val baseContext = "/api/support"

    val createTicket = callDescription<CreateTicketRequest, Unit, CommonErrorMessage> {
        name = "createTicket"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"ticket"
        }

        body { bindEntireRequestFromBody() }
    }
}
