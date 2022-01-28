package dk.sdu.cloud.support.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateTicketRequest(
    val subject: String,
    val message: String
)

@TSTopLevel
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
