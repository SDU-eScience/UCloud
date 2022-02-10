package dk.sdu.cloud.mail.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class SendRequestItem(
    val receiver: String,
    val mail: Mail,
    val mandatory: Boolean = false,
    val receivingEmail: String? = null,
    val testMail: Boolean? = null
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
    private const val baseContext = "/api/mail"

    init {
        description = """
            Internal service for sending mails to end-users.
            
            Currently only one end-point is exposed for sending a single email to one user at a time, and only
            `SERVICE` principals is authorized to do so.

            Email templates are pre-defined and are not controllable by clients.
        """.trimIndent()
    }

    override fun documentation() {
        useCase("sendToUser", "Sending an email") {
            val ucloud = ucloudCore()
            success(
                sendToUser,
                bulkRequestOf(
                    SendRequestItem(
                        "User#1234",
                        Mail.LowFundsMail(
                            listOf("u1-standard"),
                            listOf("ucloud"),
                            listOf("Science Project"),
                        ),
                    )
                ),
                Unit,
                ucloud
            )
        }
    }

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

    val sendToUser = call<BulkRequest<SendRequestItem>, Unit, CommonErrorMessage>("sendToUser") {
        httpUpdate(baseContext, "sendToUser", roles = Roles.PRIVILEGED)
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
