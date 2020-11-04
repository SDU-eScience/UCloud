package dk.sdu.cloud.mail.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.LookupEmailRequest
import dk.sdu.cloud.auth.api.LookupEmailResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.WantsEmailsRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import okhttp3.internal.UTC
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class MailService(
    private val authenticatedClient: AuthenticatedClient,
    private val fromAddress: String,
    private val whitelist: List<String>,
    private val devMode: Boolean = false,
    private val ctx: DBContext,
    private val alerting: AlertingService
) {

    object MailCounterTable: SQLTable("mail_counting") {
        val mailCount = long("mail_count", notNull = true)
        val username = text("username", notNull = true)
        val periodStart = timestamp("period_start", notNull = true)
        val alertedFor = bool("alerted_for", notNull = true)
    }

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

    private val tempDirectory by lazy { createTempDir("mails") }

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

    suspend fun send(
        principal: SecurityPrincipal,
        recipient: String,
        subject: String,
        text: String,
        emailRequestedByUser: Boolean,
        testMail: Boolean = false
    ) {
        if (principal.username !in whitelist && !devMode) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Unable to send mail")
        }
        if (!emailRequestedByUser) {
            //IF expanded upon it should be moved out of AUTH
            val wantEmails = UserDescriptions.wantsEmails.call(
                WantsEmailsRequest(principal.username),
                authenticatedClient
            ).orThrow()

            if (!wantEmails) {
                log.info("User: ${principal.username} does not want to receive emails")
                return
            }
        }

        val getEmail = if (testMail) {
            LookupEmailResponse("test@email.dk")
        } else {
            UserDescriptions.lookupEmail.call(
                LookupEmailRequest(recipient),
                authenticatedClient
            ).orThrow()
        }

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
            if (devMode) {
                fakeSend(message)
            } else {
                Transport.send(message)
            }
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
        }
    }

    private fun fakeSend(message: MimeMessage) {
        val file = createTempFile(suffix = ".html", directory = tempDirectory)
        val fileOut = FileOutputStream(file).bufferedWriter()
        val tmpOut = ByteArrayOutputStream()
        message.writeTo(tmpOut)
        val messageAsString = tmpOut.toByteArray().toString(Charsets.UTF_8)
        val lines = messageAsString.lines()
        val boundaryLine = lines.find { it.trim().startsWith("boundary=") }!!.trim()
        val boundary = "--" + boundaryLine.substring(
            boundaryLine.indexOfFirst { it == '"' } + 1,
            boundaryLine.indexOfLast { it == '"' }
        )
        val parts = messageAsString.split(boundary)
        parts.forEach { part ->
            if (part.contains("text/html")) {
                fileOut.append(part)
            } else {
                fileOut.appendln("<!--")
                fileOut.append(part)
                fileOut.appendln("--!>")
            }
        }

        log.info("email written to ${file.absolutePath}")
        tmpOut.close()
        fileOut.close()
    }

    private suspend fun incrementMailCount(username: String) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("count", 1L)
                        setParameter("username", username)
                        setParameter("time", LocalDateTime.now(DateTimeZone.UTC))
                    },
                    """
                        INSERT INTO mail_counting 
                        VALUES (:count, :username, :time)
                        ON CONFLICT (username) 
                        DO 
                            UPDATE SET mail_count = mail_counting.mail_count + 1
                    """
                )
        }
    }

    private suspend fun resetMailCount(username: String) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("time", LocalDateTime.now(DateTimeZone.UTC))
                        setParameter("count", 1L)
                        setParameter("alertedFor", false)
                    },
                    """
                        UPDATE mail_counting
                        SET period_start = :time,
                            mail_count = :count,
                            alerted_for = :alertedFor
                        WHERE username = :username
                    """
                )
        }
    }

    suspend fun allowedToSend(recipient: String): Boolean {
        incrementMailCount(recipient)
        val countInfoForUser = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", recipient)
                    },
                    """
                        SELECT *
                        FROM mail_counting
                        WHERE username = :username
                    """
                ).rows
                .singleOrNull()
                ?.toMailCountInfo()
                ?: throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError,
                    "Mail count does not exist. Should just have been initialized or updated")
        }
        if(LocalDateTime.now(DateTimeZone.UTC).isAfter(countInfoForUser.timestamp.plusMinutes(30))) {
            resetMailCount(recipient)
            return true
        } else {
            when {
                countInfoForUser.count > 60 -> {
                    if (!countInfoForUser.alertedFor) {
                        alerting.createAlert(
                            Alert(
                                "Mail service have exceeded 60 attempts of sending a mail to $recipient"
                            )
                        )
                        ctx.withSession { session ->
                            session.sendPreparedStatement(
                                {
                                    setParameter("username", recipient)
                                },
                                """
                                    UPDATE mail_counting
                                    SET alerted_for = NOT alerted_for
                                    WHERE username = :username
                                """
                            )
                        }
                    }
                    return false
                }
                countInfoForUser.count > 20 -> {
                    return false
                }
                else -> {
                    return true
                }
            }
        }
    }

    data class MailCountInfo(
        val username: String,
        val timestamp: LocalDateTime,
        val count: Long,
        val alertedFor: Boolean
    )

    fun RowData.toMailCountInfo(): MailCountInfo {
        return MailCountInfo(
            getField(MailCounterTable.username),
            getField(MailCounterTable.periodStart),
            getField(MailCounterTable.mailCount),
            getField(MailCounterTable.alertedFor)
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
