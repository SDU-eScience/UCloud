package dk.sdu.cloud.mail.services

import dk.sdu.cloud.auth.api.EmailExistsRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import io.ktor.http.HttpStatusCode
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailService(private val authenticatedClient: AuthenticatedClient) {
    private val from = "support@escience.sdu.dk"

    suspend fun send(recipient: String, subject: String, text: String) {

        val emailCheck = UserDescriptions.emailExists.call(EmailExistsRequest(recipient)
        , authenticatedClient).orRethrowAs {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "User with given email address does not exist")
        }

        val recipientAddress = if (emailCheck.exists) {
            InternetAddress(recipient)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "User with given email address does not exist")
        }

        // Setup mail server
        val properties = System.getProperties()
        properties.setProperty("mail.smtp.host", "localhost")
        properties.setProperty("mail.smtp.port", "25")

        val session = Session.getInstance(properties)

        try {
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(from))

            message.addRecipient(Message.RecipientType.TO, recipientAddress)

            message.subject = subject
            message.setText(text)

            Transport.send(message);
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }
}
