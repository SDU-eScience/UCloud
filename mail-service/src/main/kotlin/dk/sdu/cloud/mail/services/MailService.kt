package dk.sdu.cloud.mail.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.LookupEmailRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
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
    private val escienceLogoFile: File by lazy {
        val file = Files.createTempFile("", ".png").toFile()
        file.outputStream().use {
            javaClass.classLoader.getResourceAsStream("sdu_escience_logo.png")!!.copyTo(it)
        }
        return@lazy file
    }

    private val ucloudLogoFile: File by lazy {
        val file = Files.createTempFile("", ".png").toFile()
        file.outputStream().use {
            javaClass.classLoader.getResourceAsStream("ucloud_logo.png")!!.copyTo(it)
        }
        return@lazy file
    }

    private fun addTemplate(text: String): String {
        return """
        <!DOCTYPE HTML>
        <html>
            <head>
                <style type="text/css" media="screen">
                    a {
                        color: #0069FD;
                        text-decoration: none;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body style='margin:0; padding:0; font-family: "IBM Plex Sans", sans-serif, system-ui, -apple-system,
                Segoe UI, Roboto, Ubuntu, Cantarell, Oxygen, sans-serif, "Apple Color Emoji", "Segoe UI Emoji",
                "Segoe UI Symbol", "Noto Color Emoji";'>
                <div style="padding: .4em .3em .1em 2em; background: #0069FD;">
                    <a href="https://cloud.sdu.dk">
                        <img style="margin-right: .2em;" src="cid:ucloud_logo">
                    </a>
                </div>
                <div style="padding: 2em 4em 2em 4em; max-width: 600px; margin-left: auto; margin-right: auto;">
                    $text
                    <p>Best regards,</p>
                    <p><strong>The UCloud Team<br>SDU eScience Center</strong></p>
                </div>
                <div style="text-align: right; padding: 1em;">
                    <a href="https://escience.sdu.dk"><img src="cid:escience_logo"></a>
                </div>
            </body>
        </html>
        """.trimIndent()
    }

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

            // style paragraphs (not a good solution, but with best support)
            val finalText = text.replace("<p>", "<p style=\"line-height: 1.2em; margin:0 0 1em 0;\">")

            val bodyPart = MimeBodyPart()
            val bodyWithTemplate = addTemplate(finalText)

            bodyPart.setContent(bodyWithTemplate, "text/html")
            multipart.addBodyPart(bodyPart)

            multipart.addBodyPart(MimeBodyPart().apply {
                disposition = MimeBodyPart.INLINE
                contentID = "escience_logo"
                attachFile(escienceLogoFile)
            })

            multipart.addBodyPart(MimeBodyPart().apply {
                disposition = MimeBodyPart.INLINE
                contentID = "ucloud_logo"
                attachFile(ucloudLogoFile)
            })

            message.setContent(multipart)

            Transport.send(message)
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }
}
