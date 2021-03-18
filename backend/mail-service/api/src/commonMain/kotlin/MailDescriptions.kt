package dk.sdu.cloud.mail.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class SendRequest(
    val receiver: String,
    val mail: Mail,
    val mandatory: Boolean = false
)

@Serializable
data class SendBulkRequest(
    val messages: List<SendRequest>
)

@Serializable
data class SendSupportEmailRequest(
    val fromEmail: String,
    val subject: String,
    val message: String
)

typealias SendSupportEmailResponse = Unit

@Serializable
data class EmailSettingsItem(
    val username: String? = null,
    val settings: EmailSettings
)

typealias ToggleEmailSettingsRequest = BulkRequest<EmailSettingsItem>

typealias ToggleEmailSettingsResponse = Unit

@Serializable
data class RetrieveEmailSettingsRequest(
    val username: String? = null
)
@Serializable
data class RetrieveEmailSettingsResponse(
    val settings: EmailSettings
)


@TSTopLevel
object MailDescriptions : CallDescriptionContainer("mail") {
    val baseContext = "/api/mail"

    val sendSupport = call<SendSupportEmailRequest, SendSupportEmailResponse, CommonErrorMessage>("sendSupport") {
        auth {
            roles = setOf(Role.SERVICE)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"support"
            }

            body { bindEntireRequestFromBody() }
        }
    }

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

    val toggleEmailSettings = call<
        ToggleEmailSettingsRequest,
        ToggleEmailSettingsResponse,
        CommonErrorMessage>("toggleEmailSettings")
    {
            httpUpdate(
                baseContext,
                "toggleEmailSettings"
            )
    }

    val retrieveEmailSettings = call<
        RetrieveEmailSettingsRequest,
        RetrieveEmailSettingsResponse,
        CommonErrorMessage>("retrieveEmailSettings")
    {
        httpRetrieve(
            baseContext,
            "emailSettings"
        )
    }
}
