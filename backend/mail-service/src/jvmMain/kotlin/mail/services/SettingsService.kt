package mail.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.mail.api.EmailSettings
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement

object EmailSettingsTable: SQLTable("email_settings") {
    val username = text("username", notNull = true)
    val settings = jsonb("settings", notNull = true)
}

class SettingsService(
    private val db: DBContext
) {

    suspend fun updateEmailSettings(emailSettings: EmailSettings, username: String) {
        val json = defaultMapper.encodeToJsonElement(emailSettings)
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("json", json.toString())
                        setParameter("username", username)
                    },
                    """
                        INSERT INTO email_settings (username, settings) 
                        VALUES (:username, :json::jsonb) 
                        ON CONFLICT (username)
                        DO UPDATE SET settings = :json::jsonb
                    """
                )
        }
    }

    suspend fun getEmailSettings(username: String): EmailSettings {
        return db.withSession { session ->
            val settings = session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    SELECT * 
                    FROM email_settings
                    WHERE username = :username
                """
            ).rows
                .singleOrNull()
                ?.getField(EmailSettingsTable.settings)
            if (settings == null) {
                EmailSettings()
            }
            else {
                defaultMapper.decodeFromString<EmailSettings>(settings)
            }
        }
    }

    suspend fun wantEmail(username: String, mail: Mail):Boolean {
        val settings = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        SELECT * 
                        FROM email_settings
                        WHERE username = :username
                    """
                ).rows
                .singleOrNull()
                ?.getField(EmailSettingsTable.settings)
        }
        if (settings == null) {
            return true
        }
        else {
            val mapped = defaultMapper.decodeFromString<EmailSettings>(settings)
            return when (mail) {
                is Mail.TransferApplicationMail -> mapped.applicationTransfer
                is Mail.GrantApplicationWithdrawnMail -> mapped.grantApplicationWithdrawn
                is Mail.GrantApplicationRejectedMail -> mapped.grantApplicationRejected
                is Mail.GrantApplicationApproveMail -> mapped.grantApplicationApproved
                is Mail.GrantApplicationStatusChangedToAdmin -> mapped.applicationStatusChange
                is Mail.GrantApplicationUpdatedMailToAdmins -> mapped.grantApplicationUpdated
                is Mail.GrantApplicationUpdatedMail -> mapped.grantApplicationUpdated
                is Mail.GrantAppAutoApproveToAdminsMail -> mapped.grantAutoApprove
                is Mail.GrantApplicationApproveMailToAdmins -> mapped.grantApplicationApproved
                is Mail.NewGrantApplicationMail -> mapped.newGrantApplication
                is Mail.LowFundsMail -> mapped.lowFunds
                is Mail.StillLowFundsMail -> mapped.lowFunds
                is Mail.NewCommentOnApplicationMail -> mapped.newCommentOnApplication
                is Mail.ProjectInviteMail -> mapped.projectUserInvite
                is Mail.ResetPasswordMail -> true
                is Mail.UserLeftMail -> mapped.userLeft
                is Mail.UserRemovedMail -> mapped.projectUserRemoved
                is Mail.UserRemovedMailToUser -> mapped.projectUserRemoved
                is Mail.UserRoleChangeMail -> mapped.userRoleChange
                is Mail.VerificationReminderMail -> mapped.verificationReminder
                else -> {
                    throw RPCException.fromStatusCode(
                        HttpStatusCode.InternalServerError, "Mapping from mail to setting not found"
                    )
                }
            }
        }
    }
}
