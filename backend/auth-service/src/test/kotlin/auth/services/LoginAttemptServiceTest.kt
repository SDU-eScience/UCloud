package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.math.pow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginAttemptServiceTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(AuthServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun before() {
        dbTruncate(db)
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    val time = Time.now()
    var currentTime: Long = 0
    private val dao = LoginAttemptAsyncDao { currentTime + time }
    val user = "user"

    @Test
    fun `test that login attempts are allowed`(): Unit = runBlocking {
        db.withSession { session ->
            repeat(LoginAttemptAsyncDao.LOCKOUT_THRESHOLD) {
                assertNull(dao.timeUntilNextAllowedLogin(session, user))
                dao.logAttempt(session, user)
            }
        }
    }

    @Test
    fun `test that attempts are blocked`(): Unit = runBlocking {
        repeat(LoginAttemptAsyncDao.LOCKOUT_THRESHOLD + 1) {
            dao.logAttempt(db, user)
        }

        assertNotNull(dao.timeUntilNextAllowedLogin(db, user))
        assertEquals(
            LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L,
            dao.timeUntilNextAllowedLogin(db, user)
        )

        currentTime = LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L + 1
        assertNull(dao.timeUntilNextAllowedLogin(db, user))
    }

    @Test
    fun `test that time increases and decreases`(): Unit = runBlocking {
        repeat(LoginAttemptAsyncDao.LOCKOUT_THRESHOLD + 1) {
            dao.timeUntilNextAllowedLogin(db, user)
            dao.logAttempt(db, user)
        }

        currentTime = LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L + 1
        assertNull(dao.timeUntilNextAllowedLogin(db, user))

        repeat(LoginAttemptAsyncDao.LOCKOUT_THRESHOLD) {
            assertNull(dao.timeUntilNextAllowedLogin(db, user))
            dao.logAttempt(db, user)
        }

        dao.logAttempt(db, user)
        assertEquals(
            LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(2).toLong() * 1000L,
            dao.timeUntilNextAllowedLogin(db, user)
        )

        currentTime += LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(2).toLong() * 1000L +
                LoginAttemptAsyncDao.COOLDOWN_EXPIRY + 1000

        assertNull(dao.timeUntilNextAllowedLogin(db, user))

        repeat(LoginAttemptAsyncDao.LOCKOUT_THRESHOLD + 1) {
            dao.timeUntilNextAllowedLogin(db, user)
            dao.logAttempt(db, user)
        }

        assertEquals(
            LoginAttemptAsyncDao.LOCKOUT_DURATION_BASE_SECONDS.toLong() * 1000L,
            dao.timeUntilNextAllowedLogin(db, user)
        )

    }
}
