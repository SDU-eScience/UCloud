package dk.sdu.cloud.mail.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.mail.api.EmailSettings
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement

class SettingsService(
    private val db: DBContext
) {

    suspend fun updateEmailSettings(session: DBContext, emailSettings: EmailSettings, username: String) {
        val json = defaultMapper.encodeToJsonElement(emailSettings)
        session.withSession { session ->
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
                    SELECT  settings
                    FROM email_settings
                    WHERE username = :username
                """
            ).rows
                .singleOrNull()
                ?.getString(0)
            if (settings == null) {
                EmailSettings()
            }
            else {
                defaultMapper.decodeFromString<EmailSettings>(settings)
            }
        }
    }

    suspend fun wantEmail(username: String, mail: Mail):Boolean {
        val settings = getEmailSettings(username)
        return when (mail) {
            is Mail.TransferApplicationMail -> settings.applicationTransfer
            is Mail.GrantApplicationWithdrawnMail -> settings.grantApplicationWithdrawn
            is Mail.GrantApplicationRejectedMail -> settings.grantApplicationRejected
            is Mail.GrantApplicationApproveMail -> settings.grantApplicationApproved
            is Mail.GrantApplicationStatusChangedToAdmin -> settings.applicationStatusChange
            is Mail.GrantApplicationUpdatedMailToAdmins -> settings.grantApplicationUpdated
            is Mail.GrantApplicationUpdatedMail -> settings.grantApplicationUpdated
            is Mail.GrantAppAutoApproveToAdminsMail -> settings.grantAutoApprove
            is Mail.GrantApplicationApproveMailToAdmins -> settings.grantApplicationApproved
            is Mail.NewGrantApplicationMail -> settings.newGrantApplication
            is Mail.LowFundsMail -> settings.lowFunds
            is Mail.StillLowFundsMail -> settings.lowFunds
            is Mail.NewCommentOnApplicationMail -> settings.newCommentOnApplication
            is Mail.ProjectInviteMail -> settings.projectUserInvite
            is Mail.ResetPasswordMail -> true
            is Mail.UserLeftMail -> settings.userLeft
            is Mail.UserRemovedMail -> settings.projectUserRemoved
            is Mail.UserRemovedMailToUser -> settings.projectUserRemoved
            is Mail.UserRoleChangeMail -> settings.userRoleChange
            is Mail.VerificationReminderMail -> settings.verificationReminder
            else -> {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError, "Mapping from mail to setting not found"
                )
            }
        }
    }
}
