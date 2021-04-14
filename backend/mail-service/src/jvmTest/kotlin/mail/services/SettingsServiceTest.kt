package mail.services

import dk.sdu.cloud.mail.api.EmailSettings
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailServiceDescription
import dk.sdu.cloud.mail.services.SettingsService
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.*

class SettingsServiceTest {
    companion object {
        private lateinit var embDB: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db, embDB) = TestDB.from(MailServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun after() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun beforeEach() {
        truncate(db)
    }

    @AfterTest
    fun afterEach() {
        truncate(db)
    }

    private fun truncate(db: DBContext) {
        runBlocking {
            db.withSession { session ->
                session.sendPreparedStatement(
                    """
                        TRUNCATE mail_counting, email_settings
                    """
                )
            }
        }
    }

    @Test
    fun `get Settings, set and see update`() {
        val settingsService = SettingsService(db)
        val user = TestUsers.user
        //Should be default (ALL TRUE)
        runBlocking {
            db.withSession { ctx ->
                val settings = settingsService.getEmailSettings(user.username)
                for (prop in EmailSettings::class.memberProperties) {
                    assertEquals(true, prop.get(settings))
                }
                val newSettings = EmailSettings(
                    grantApplicationApproved = false
                )

                settingsService.updateEmailSettings(ctx, newSettings, user.username)

                val updatedSettings = settingsService.getEmailSettings(user.username)
                assertEquals(true, updatedSettings.applicationStatusChange)
                assertEquals(true, updatedSettings.applicationTransfer)
                assertEquals(false, updatedSettings.grantApplicationApproved)
                assertEquals(true, updatedSettings.grantApplicationRejected)
                assertEquals(true, updatedSettings.grantApplicationWithdrawn)
                assertEquals(true, updatedSettings.grantApplicationUpdated)
                assertEquals(true, updatedSettings.lowFunds)
                assertEquals(true, updatedSettings.newCommentOnApplication)
                assertEquals(true, updatedSettings.newGrantApplication)
                assertEquals(true, updatedSettings.projectUserInvite)
                assertEquals(true, updatedSettings.projectUserRemoved)
                assertEquals(true, updatedSettings.userLeft)
                assertEquals(true, updatedSettings.userRoleChange)
                assertEquals(true, updatedSettings.verificationReminder)

                val newSettingsAgain = EmailSettings(
                    grantApplicationApproved = false,
                    grantApplicationWithdrawn = false
                )
                settingsService.updateEmailSettings(ctx, newSettings, TestUsers.user2.username)
                settingsService.updateEmailSettings(ctx, newSettingsAgain, user.username)

                val updatedSettingsAfterConflict = settingsService.getEmailSettings(user.username)
                assertEquals(true, updatedSettingsAfterConflict.applicationStatusChange)
                assertEquals(true, updatedSettingsAfterConflict.applicationTransfer)
                assertEquals(false, updatedSettingsAfterConflict.grantApplicationApproved)
                assertEquals(true, updatedSettingsAfterConflict.grantApplicationRejected)
                assertEquals(false, updatedSettingsAfterConflict.grantApplicationWithdrawn)
                assertEquals(true, updatedSettingsAfterConflict.grantApplicationUpdated)
                assertEquals(true, updatedSettingsAfterConflict.lowFunds)
                assertEquals(true, updatedSettingsAfterConflict.newCommentOnApplication)
                assertEquals(true, updatedSettingsAfterConflict.newGrantApplication)
                assertEquals(true, updatedSettingsAfterConflict.projectUserInvite)
                assertEquals(true, updatedSettingsAfterConflict.projectUserRemoved)
                assertEquals(true, updatedSettingsAfterConflict.userLeft)
                assertEquals(true, updatedSettingsAfterConflict.userRoleChange)
                assertEquals(true, updatedSettingsAfterConflict.verificationReminder)

            }
        }
    }

    @Test
    fun `Test wants Mails`() {
        val settingsService = SettingsService(db)
        val user = TestUsers.user
        val newSettings = EmailSettings(
            grantApplicationApproved = false
        )
        val verificationReminderMail = Mail.VerificationReminderMail(
            "projectTitle",
            "ADMIN"
        )

        val grantApproved = Mail.GrantApplicationApproveMail(
            "ProjectTitle"
        )

        runBlocking {
            db.withSession { ctx ->
                settingsService.updateEmailSettings(ctx, newSettings, user.username)

                val wantVerification = settingsService.wantEmail(user.username, verificationReminderMail)
                assertTrue(wantVerification)
                val wantAppApproved = settingsService.wantEmail(user.username, grantApproved)
                assertFalse(wantAppApproved)
            }
        }
    }

    @Test
    fun `fun test default want`() {
        val settingsService = SettingsService(db)
        val user = TestUsers.user
        val verificationReminderMail = Mail.VerificationReminderMail(
            "projectTitle",
            "ADMIN"
        )
        runBlocking {
            val wantVerification = settingsService.wantEmail(user.username, verificationReminderMail)
            assertTrue(wantVerification)
        }
    }
}
