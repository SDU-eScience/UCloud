package dk.sdu.cloud.mail.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.mail.api.MailRecipient
import io.ktor.http.HttpStatusCode
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailService {
    fun send(recipients: List<MailRecipient>, subject: String, text: String) {
        val from = "escience@sdu.dk"

        // Fetch email address of each recipient
        val recipientAddresses = recipients.map{
            // TODO perform lookup
            InternetAddress(it.id)
        }

        // Get system properties
        val properties = System.getProperties()

        // Setup mail server
        properties.setProperty("mail.smtp.host", "localhost")
        properties.setProperty("mail.smtp.port", "465")

        val session = Session.getInstance(properties)

        try {
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(from))

            recipientAddresses.forEach {
                message.addRecipient(Message.RecipientType.TO, it)
            }

            message.subject = subject
            message.setText(text)

            // Send message
            Transport.send(message);
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }
}
