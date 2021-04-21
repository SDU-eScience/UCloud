package dk.sdu.cloud.mail.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.mail.api.MailServiceDescription
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import dk.sdu.cloud.mail.services.MailService.MailCountInfo
import dk.sdu.cloud.slack.api.SlackDescriptions
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailServiceTest {

    companion object {
        private lateinit var embDB: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db,embDB) = TestDB.from(MailServiceDescription)
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
                        TRUNCATE mail_counting
                    """
                )
            }
        }
    }

    private suspend fun getInfo(username: String, db: DBContext): MailCountInfo? {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    SELECT *
                    FROM mail_counting
                    WHERE username = :username
                """
            ).rows
                .singleOrNull()
                ?.toMailCountInfo()
        }
    }

    private suspend fun setTime(username: String, timestamp: LocalDateTime, db: DBContext) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", username)
                    setParameter("time", timestamp)
                },
                """
                    UPDATE mail_counting
                    SET period_start = :time
                    WHERE username = :username
                """
            )
        }
    }

    fun RowData.toMailCountInfo(): MailCountInfo {
        return MailCountInfo(
            getField(MailService.MailCounterTable.username),
            getField(MailService.MailCounterTable.periodStart),
            getField(MailService.MailCounterTable.mailCount),
            getField(MailService.MailCounterTable.alertedFor)
        )
    }

    @Test
    fun `Allowed to send`() {
        val mockedSettingsService = mockk<SettingsService>()
        val service = MailService(
            ClientMock.authenticatedClient,
            "support@escience.sdu.dk",
            emptyList(),
            true,
            db,
            mockedSettingsService
        )
        val user = TestUsers.user
        val user2 = TestUsers.user2
        runBlocking {
            run {
                val info = getInfo(user.username, db)
                assertNull(info)
            }
            run {
                val allowed = service.allowedToSend(user.username)
                val info = getInfo(user.username, db)
                assertTrue(allowed)
                assertNotNull(info)
                assertEquals(1, info.count)
            }
            run {
                val infoBefore = getInfo(user2.username, db)
                assertNull(infoBefore)
                val allowed = service.allowedToSend(user2.username)
                val infoAfter = getInfo(user2.username, db)
                assertTrue(allowed)
                assertNotNull(infoAfter)
                assertEquals(1, infoAfter.count)
            }
            run {
                val allowed = service.allowedToSend(user.username)
                val info = getInfo(user.username, db)
                assertTrue(allowed)
                assertNotNull(info)
                assertEquals(2, info.count)
            }

            run {
                for (i in 0..20) {
                    service.allowedToSend(user.username)
                }
                val allowed = service.allowedToSend(user.username)
                assertFalse(allowed)
                val info = getInfo(user.username, db)
                assertNotNull(info)
                assertEquals(24, info.count)
                assertFalse(info.alertedFor)
            }
            run {
                ClientMock.mockCallSuccess(
                    SlackDescriptions.sendAlert,
                    Unit
                )
                for (i in 0..40) {
                    service.allowedToSend(user.username)
                }
                val allowed = service.allowedToSend(user.username)
                assertFalse(allowed)
                val info = getInfo(user.username, db)
                assertNotNull(info)
                assertEquals(66, info.count)
                assertTrue(info.alertedFor)
            }
            setTime(user.username, LocalDateTime.now(DateTimeZone.UTC).minusHours(1), db)
            run {
                val allowed = service.allowedToSend(user.username)
                assertTrue(allowed)
                val info = getInfo(user.username, db)
                assertNotNull(info)
                assertFalse(info.alertedFor)
                assertEquals(1, info.count)
            }
        }
    }
}
