package dk.sdu.cloud.mail.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.SendDirectMandatoryEmailRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.mail.utils.*
import dk.sdu.cloud.service.escapeHtml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.OffsetDateTime
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
    private val fakeSendEmails: Boolean = false,
    private val ctx: DBContext,
    private val settingsService: SettingsService
) {
    private var session: Session

    init {
        //Setup Mail Server
        val properties = System.getProperties()
        properties.setProperty("mail.smtp.host", "localhost")
        properties.setProperty("mail.smtp.port", "25")
        properties.setProperty("mail.smtp.allow8bitmime", "true");
        properties.setProperty("mail.smtps.allow8bitmime", "true");

        session = Session.getInstance(properties)
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
                        color: #096DE3;
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
                <div style="padding: .4em .3em .1em 2em; background: #096DE3;">
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

    private fun addSupportTemplate(text: String): String {
        return """
        <!DOCTYPE HTML>
        <html>
            <body style='margin:0; padding:0; font-family: "IBM Plex Sans", sans-serif, system-ui, -apple-system,
                Segoe UI, Roboto, Ubuntu, Cantarell, Oxygen, sans-serif, "Apple Color Emoji", "Segoe UI Emoji",
                "Segoe UI Symbol", "Noto Color Emoji";'>
                <div style="padding: 2em 4em 2em 4em; max-width: 600px; margin-left: auto; margin-right: auto;">
                    $text
                </div>
            </body>
        </html>
        """.trimIndent()
    }

    suspend fun sendSupportTicket(
        userEmail: String,
        subject: String,
        text: String
    ) {
        val recipientAddress = InternetAddress("support@escience.sdu.dk")

        try {
            val message = MimeMessage(session)
            message.setFrom("ticketsystem@escience.sdu.dk")
            message.addRecipient(Message.RecipientType.TO, recipientAddress)
            message.subject = "$userEmail-|-$subject"
            val multipart = MimeMultipart()

            // style paragraphs (not a good solution, but with best support)
            val finalText = text.replace("<p>", "<p style=\"line-height: 1.2em; margin:0 0 1em 0;\">")

            val bodyPart = MimeBodyPart()
            val bodyWithTemplate = addSupportTemplate(finalText)

            bodyPart.setContent(bodyWithTemplate, "text/html")
            multipart.addBodyPart(bodyPart)

            message.setContent(multipart)
            if (fakeSendEmails) {
                fakeSend(message)
            } else {
                Transport.send(message)
            }
        } catch (e: Throwable) {
            throw RuntimeException("Unable to send email", e)
        }
    }

    suspend fun sendDirect(items: List<SendDirectMandatoryEmailRequest>) {
        for (item in items) {
            sendEmail(item.recipientEmail, item.mail, item.recipientEmail, fakeSendEmails)
        }
    }

    suspend fun send(
        principal: SecurityPrincipal,
        recipient: String,
        mail: Mail,
        emailRequestedByUser: Boolean,
        testMail: Boolean? = false,
        recipientEmail: String? = null
    ) {
        if (principal.username !in whitelist && !fakeSendEmails) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Unable to send mail")
        }
        if (!emailRequestedByUser) {
            //IF expanded upon it should be moved out of AUTH
            val wantsEmails = settingsService.wantEmail(recipient, mail)

            if (!wantsEmails) {
                log.info("User: $recipient does not want to receive emails")
                return
            }
        }

        val receivingEmail = recipientEmail
            ?: if (testMail == true) {
                "test@email.dk"
            } else {
                ctx.withSession { session ->
                    session.sendPreparedStatement(
                        {
                            setParameter("username", recipient)
                        },
                        """
                                SELECT email 
                                FROM "auth".principals
                                WHERE :username=id
                            """
                    ).rows
                        .singleOrNull()
                        ?.getString(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }

        sendEmail(receivingEmail, mail, recipient, testMail == true)
    }

    private fun sendEmail(
        emailAddress: String,
        letter: Mail,
        recipientName: String,
        testMail: Boolean = false,
    ) {

        val recipientAddress = InternetAddress(emailAddress)

        val text = when (letter) {
            is Mail.TransferApplicationMail -> {
                transferOfApplication(
                    recipientName,
                    letter.senderProject,
                    letter.receiverProject,
                    letter.applicationProjectTitle
                )
            }

            is Mail.GrantApplicationWithdrawnMail -> {
                closed(recipientName, letter.projectTitle, letter.sender)
            }

            is Mail.GrantApplicationRejectedMail -> {
                rejected(recipientName, letter.projectTitle)
            }

            is Mail.GrantApplicationApproveMail -> {
                approved(recipientName, letter.projectTitle)
            }

            is Mail.GrantApplicationStatusChangedToAdmin -> {
                statusChangeTemplateToAdmins(letter.status, recipientName, letter.sender, letter.projectTitle)
            }

            is Mail.GrantApplicationUpdatedMailToAdmins -> {
                updatedTemplateToAdmins(letter.projectTitle, recipientName, letter.sender, letter.receivingProjectTitle)
            }

            is Mail.GrantApplicationUpdatedMail -> {
                updatedTemplate(letter.projectTitle, recipientName, letter.sender)
            }

            is Mail.GrantApplicationApproveMailToAdmins -> {
                approvedProjectToAdminsTemplate(recipientName, letter.sender, letter.projectTitle)
            }

            is Mail.NewGrantApplicationMail -> {
                newIngoingApplicationTemplate(recipientName, letter.sender, letter.projectTitle)
            }

            is Mail.LowFundsMail -> {
                val walletLines = mutableListOf<String>()
                letter.projectTitles.forEachIndexed { index, projectTitle ->
                    val resourceLine =
                        "<li>Resource: ${escapeHtml(letter.categories[index])}</li> <li>Provider: ${escapeHtml(letter.providers[index])}</li>"
                    if (projectTitle != null) {
                        walletLines.add("<li>Project: ${escapeHtml(projectTitle)} <ul> $resourceLine </ul> </li>")
                    } else {
                        walletLines.add("<li>Own workspace, <ul>$resourceLine</ul></li>")
                    }
                }
                lowResourcesTemplate(recipientName, walletLines)
            }

            is Mail.StillLowFundsMail -> {
                stillLowResources(recipientName, letter.category, letter.provider, letter.projectTitle)
            }

            is Mail.NewCommentOnApplicationMail -> {
                newCommentTemplate(recipientName, letter.sender, letter.projectTitle, letter.receivingProjectTitle)
            }

            is Mail.ProjectInviteMail -> {
                userInvitedToInviteeTemplate(recipientName, letter.projectTitle)
            }

            is Mail.ResetPasswordMail -> {
                resetPasswordTemplate(recipientName, letter.token)
            }

            is Mail.UserLeftMail -> {
                userLeftTemplate(recipientName, letter.leavingUser, letter.projectTitle)
            }

            is Mail.UserRemovedMail -> {
                userRemovedTemplate(recipientName, letter.leavingUser, letter.projectTitle)
            }

            is Mail.UserRemovedMailToUser -> {
                userRemovedToUserTemplate(recipientName, letter.projectTitle)
            }

            is Mail.UserRoleChangeMail -> {
                userRoleChangeTemplate(recipientName, letter.subjectToChange, letter.roleChange, letter.projectTitle)
            }

            is Mail.VerificationReminderMail -> {
                verifyReminderTemplate(recipientName, letter.projectTitle, letter.role)
            }

            is Mail.VerifyEmailAddress -> {
                verifyEmailAddress(letter.verifyType, letter.username ?: recipientName, letter.token)
            }

            is Mail.JobEvents -> {
                jobEventsTemplate(recipientName, letter.jobIds, letter.jobNames, letter.appTitles, letter.events)
            }

            else -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError,
                    "Mail type does not exist. Missing template for email."
                )
            }
        }

        try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(fromAddress, "eScience Support"))
            message.addRecipient(Message.RecipientType.TO, recipientAddress)
            message.subject = letter.subject

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
            if (fakeSendEmails || testMail == true) {
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
                    },
                    """
                        INSERT INTO mail_counting 
                        VALUES (:count, :username, now())
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
                        setParameter("count", 1L)
                        setParameter("alertedFor", false)
                    },
                    """
                        UPDATE mail_counting
                        SET period_start = now(),
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
        if(LocalDateTime.now().isAfter(countInfoForUser.timestamp.plusMinutes(30))) {
            resetMailCount(recipient)
            return true
        } else {
            when {
                countInfoForUser.count > 60 -> {
                    if (!countInfoForUser.alertedFor) {
                        SlackDescriptions.sendAlert.call(
                            SendAlertRequest(
                                "Mail service have exceeded 60 attempts of sending a mail to $recipient"
                            ),
                            authenticatedClient
                        ).orThrow()
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

    private fun RowData.toMailCountInfo(): MailCountInfo {
        return MailCountInfo(
            getString("username")!!,
            getAs<LocalDateTime>("period_start")!!,
            getLong("mail_count")!!,
            getBoolean("alerted_for")!!
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
