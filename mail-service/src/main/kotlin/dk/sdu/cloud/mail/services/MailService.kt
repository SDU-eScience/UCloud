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
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

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

            val multipart = MimeMultipart()

            val bodyPart = MimeBodyPart()
            val template = """
                <!DOCTYPE HTML>
                <html>
                    <body style='margin:0; padding:0; font-family: "IBM Plex Sans", sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Oxygen, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji";'>
                        <div style="padding: .4em .3em .1em 2em; background: #0069FD;">
                            <a href="https://cloud.sdu.dk">
                                <img style="margin-right: .2em;" src="cid:ucloud_logo">
                            </a>
                        </div>
                        <div style="padding: 2em 4em 2em 4em; max-width: 600px; margin-left: auto; margin-right: auto;">
                            $text
                            
                            <p>Best regards,</p>
                            <p>
                                <strong>The UCloud Team<br>SDU eScience Center</strong>
                            </p>
                        </div> 
                        <div style="text-align: right; padding: 1em;">
                            <img src="cid:escience_logo">
                        </div>
                    </body>
                </html> 
            """.trimIndent()

            bodyPart.setContent(template, "text/html")
            multipart.addBodyPart(bodyPart)

            val escienceLogo = MimeBodyPart()
            escienceLogo.disposition = MimeBodyPart.INLINE
            escienceLogo.contentID = "escience_logo"
            escienceLogo.attachFile("sdu_escience_logo.png")
            multipart.addBodyPart(escienceLogo)

            val ucloudLogo = MimeBodyPart()
            ucloudLogo.disposition = MimeBodyPart.INLINE
            ucloudLogo.contentID = "ucloud_logo"
            ucloudLogo.attachFile("ucloud_logo.png")
            multipart.addBodyPart(ucloudLogo)

            message.setContent(multipart)

            Transport.send(message)
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }
}
