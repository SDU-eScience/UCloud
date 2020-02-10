package dk.sdu.cloud.mail.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.mail.api.MailRecipient
import io.ktor.http.HttpStatusCode
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailService {
    fun send(recipients: List<MailRecipient>, subject: String, text: String) {
        val from = "support@escience.sdu.dk"

        val recipientAddresses = recipients.map{
            InternetAddress(it.email)
        }

        // Setup mail server
        val properties = System.getProperties()
        properties.setProperty("mail.smtp.host", "localhost")
        properties.setProperty("mail.smtp.port", "25")

        val session = Session.getInstance(properties)

        try {
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(from))

            recipientAddresses.forEach {
                message.addRecipient(Message.RecipientType.BCC, it)
            }

            message.subject = subject
            message.setText(text)

            Transport.send(message);
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }
}
