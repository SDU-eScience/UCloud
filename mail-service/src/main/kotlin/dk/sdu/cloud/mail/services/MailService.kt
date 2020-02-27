package dk.sdu.cloud.mail.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.LookupEmailRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import io.ktor.http.HttpStatusCode
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailService(
    private val authenticatedClient: AuthenticatedClient,
    private val fromAddress: String,
    private val whitelist: List<String>
) {
    suspend fun send(principal: SecurityPrincipal, recipient: String, subject: String, text: String) {
        if (principal.username !in whitelist) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Unable to send mail")
        }

        val getEmail = UserDescriptions.lookupEmail.call(
            LookupEmailRequest(recipient),
            authenticatedClient
        ).orThrow()

        val recipientAddress = InternetAddress(getEmail.email)

        // Setup mail server
        val properties = System.getProperties()
        properties.setProperty("mail.smtp.host", "localhost")
        properties.setProperty("mail.smtp.port", "25")

        val session = Session.getInstance(properties)

        try {
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(fromAddress, "eScience Support"))

            message.addRecipient(Message.RecipientType.TO, recipientAddress)

            message.subject = subject
            message.setText(text)

            Transport.send(message)
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }
}
