package dk.sdu.cloud.support.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class CreateTicketRequest(
    val subject: String,
    val message: String
)

@TSTopLevel
object SupportDescriptions : CallDescriptionContainer("support") {
    private const val baseContext = "/api/support"

    init {
        description = """The support-service is implementing a basic system for support messages.

At the moment this service is sending messages to the `#devsupport`/`#support`
channels of slack. The messages are sent to slack via their webhooks feature.
This service is not specified only to work with slack, but can be hooked up to 
any chat/mail service that supports webhooks.

![](/backend/support-service/wiki/SupportFlow.png)

## Support ticket format

- User information
  - UCloud username
  - Security role
  - Real name
- Technical info
  - Request ID 
  - User agent (Browser)
- Type (Bug, Suggestion)
- Custom message from the user
 
        """.trimIndent()
    }

    override fun documentation() {
        useCase("create-ticket", "Creating a ticket") {
            val user = basicUser()
            success(
                createTicket,
                CreateTicketRequest(
                    "My subject",
                    "Consequatur harum excepturi nemo consequatur laboriosam repellendus vel quos."
                ),
                Unit,
                user
            )
        }
    }

    val createTicket = call("createTicket", CreateTicketRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
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
