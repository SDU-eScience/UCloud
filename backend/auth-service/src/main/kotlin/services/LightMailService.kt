package dk.sdu.cloud.auth.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

// NOTE(Dan): This entire thing is copy & pasted from the mail-service. It is important that these stay in sync.
// We cannot add a dependency on the mail service since that would cause cyclic dependencies. At a later stage, we will
// merge these services to avoid this sort of issue.

@Serializable
data class SendDirectMandatoryEmailRequest(
    val recipientEmail: String,
    val mail: Mail,
)

@Serializable
sealed class Mail {
    abstract val subject: String

    @Serializable
    @SerialName("verifyEmailAddress")
    data class VerifyEmailAddress(val token: String) : Mail() {
        override val subject: String = "[UCloud] Please verify your email"
    }
}

object Mails : CallDescriptionContainer("mail") {
    private const val baseContext = "/api/mail"

    val sendDirect = call("sendDirect", BulkRequest.serializer(SendDirectMandatoryEmailRequest.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "sendDirect", roles = Roles.PRIVILEGED)
    }
}
