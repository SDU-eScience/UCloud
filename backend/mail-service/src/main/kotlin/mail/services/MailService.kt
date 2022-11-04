package dk.sdu.cloud.mail.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
//import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.Mail
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
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.mail.utils.*
import dk.sdu.cloud.service.escapeHtml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
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
            if (devMode) {
                fakeSend(message)
            } else {
                Transport.send(message)
            }
        } catch (e: Throwable) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unable to send email")
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
        if (principal.username !in whitelist && !devMode) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Unable to send mail")
        }
        if (!emailRequestedByUser) {
            //IF expanded upon it should be moved out of AUTH
            val wantsEmails = settingsService.wantEmail(recipient, mail)

            if (!wantsEmails) {
                log.info("User: ${principal.username} does not want to receive emails")
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

        val recipientAddress = InternetAddress(receivingEmail)

        val text = when (mail) {
            is Mail.TransferApplicationMail -> {
                transferOfApplication(recipient, mail.senderProject, mail.receiverProject, mail.applicationProjectTitle)
            }
            is Mail.GrantApplicationWithdrawnMail -> {
                closed(recipient, mail.projectTitle, mail.sender)
            }
            is Mail.GrantApplicationRejectedMail -> {
                rejected(recipient, mail.projectTitle)
            }
            is Mail.GrantApplicationApproveMail -> {
                approved(recipient, mail.projectTitle)
            }
            is Mail.GrantApplicationStatusChangedToAdmin -> {
                statusChangeTemplateToAdmins(mail.status, recipient, mail.sender, mail.projectTitle)
            }
            is Mail.GrantApplicationUpdatedMailToAdmins -> {
                updatedTemplateToAdmins(mail.projectTitle, recipient, mail.sender, mail.receivingProjectTitle)
            }
            is Mail.GrantApplicationUpdatedMail -> {
                updatedTemplate(mail.projectTitle, recipient, mail.sender)
            }
            is Mail.GrantAppAutoApproveToAdminsMail -> {
                autoApproveTemplateToAdmins(recipient, mail.sender, mail.projectTitle)
            }
            is Mail.GrantApplicationApproveMailToAdmins -> {
                approvedProjectToAdminsTemplate(recipient, mail.sender, mail.projectTitle)
            }
            is Mail.NewGrantApplicationMail -> {
                newIngoingApplicationTemplate(recipient, mail.sender, mail.projectTitle)
            }
            is Mail.LowFundsMail -> {
                val walletLines = mutableListOf<String>()
                mail.projectTitles.forEachIndexed { index, projectTitle ->
                    val resourceLine = "<li>Resource: ${escapeHtml(mail.categories[index])}</li> <li>Provider: ${escapeHtml(mail.providers[index])}</li>"
                    if (projectTitle != null) {
                        walletLines.add("<li>Project: ${escapeHtml(projectTitle)} <ul> $resourceLine </ul> </li>")
                    } else {
                        walletLines.add("<li>Own workspace, <ul>$resourceLine</ul></li>")
                    }
                }
                lowResourcesTemplate(recipient, walletLines)
            }
            is Mail.StillLowFundsMail  -> {
                stillLowResources(recipient, mail.category, mail.provider, mail.projectTitle)
            }
            is Mail.NewCommentOnApplicationMail -> {
                newCommentTemplate(recipient, mail.sender, mail.projectTitle, mail.receivingProjectTitle)
            }
            is Mail.ProjectInviteMail -> {
                userInvitedToInviteeTemplate(recipient, mail.projectTitle)
            }
            is Mail.ResetPasswordMail -> {
                resetPasswordTemplate(recipient, mail.token)
            }
            is Mail.UserLeftMail -> {
                userLeftTemplate(recipient, mail.leavingUser, mail.projectTitle)
            }
            is Mail.UserRemovedMail -> {
                userRemovedTemplate(recipient, mail.leavingUser, mail.projectTitle)
            }
            is Mail.UserRemovedMailToUser -> {
                userRemovedToUserTemplate(recipient, mail.projectTitle)
            }
            is Mail.UserRoleChangeMail -> {
                userRoleChangeTemplate(recipient, mail.subjectToChange, mail.roleChange, mail.projectTitle)
            }
            is Mail.VerificationReminderMail -> {
                verifyReminderTemplate(recipient, mail.projectTitle, mail.role)
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
            message.subject = mail.subject

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
            if (devMode || testMail == true) {
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
