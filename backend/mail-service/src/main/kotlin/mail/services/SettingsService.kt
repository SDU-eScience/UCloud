package mail.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.mail.api.MailSubjects
import dk.sdu.cloud.service.db.async.*

object EmailSettingsTable: SQLTable("email_settings") {
    val username = text("username", notNull = true)
    val settings = jsonb("settings", notNull = true)
}

const val DEFAULT_EMAIL_SETTINGS = """{
"LOW_FUNDS_SUBJECT": true,
"USER_ROLE_CHANGE": true,
"USER_LEFT": true,
"PROJECT_USER_INVITE": true,
"NEW_GRANT_APPLICATION": true,
"GRANT_APP_AUTO_APPROVE": true,
"GRANT_APPLICATION_UPDATED": true,
"GRANT_APP_APPROVED": true,
"GRANT_APP_REJECTED": true,
"GRANT_APP_WITHDRAWN": true,
"COMMENT_GRANT_APPLICATION": true,
"RESET_PASSWORD": true,
"VERIFICATION_REMINDER": true
}"""

class SettingsService(
    private val authenticatedClient: AuthenticatedClient,
    private val db: DBContext
) {

    suspend fun updateEmailSettings() {

    }

    suspend fun getEmailSettings(username: String): Map<MailSubjects, Boolean> {
        db.withSession { session ->
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
        }
    }
}
