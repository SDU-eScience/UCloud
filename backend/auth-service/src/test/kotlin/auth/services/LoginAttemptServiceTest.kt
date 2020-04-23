package dk.sdu.cloud.auth.services

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginAttemptServiceTest {
    private lateinit var micro: Micro
    private lateinit var db: DBSessionFactory<HibernateSession>
    var currentTime: Long = 0
    private val dao = LoginAttemptHibernateDao { currentTime + 1_000_000_000L }
    val user = "user"

    @BeforeTest
    fun beforeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        db = micro.hibernateDatabase
    }

    @Test
    fun `test that login attempts are allowed`(): Unit = runBlocking {
        db.withTransaction { session ->
            repeat(LoginAttemptHibernateDao.LOCKOUT_THRESHOLD) {
                assertNull(dao.timeUntilNextAllowedLogin(session, user))
                dao.logAttempt(session, user)
            }
        }
    }

    @Test
    fun `test that attempts are blocked`(): Unit = runBlocking {
        db.withTransaction { session ->
            repeat(LoginAttemptHibernateDao.LOCKOUT_THRESHOLD + 1) {
                dao.logAttempt(session, user)
            }

            assertNotNull(dao.timeUntilNextAllowedLogin(session, user))
            assertEquals(
                LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L,
                dao.timeUntilNextAllowedLogin(session, user)
            )

            currentTime = LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L + 1
            assertNull(dao.timeUntilNextAllowedLogin(session, user))
        }
    }

    @Test
    fun `test that time increases and decreases`(): Unit = runBlocking {
        db.withTransaction { session ->
            repeat(LoginAttemptHibernateDao.LOCKOUT_THRESHOLD + 1) {
                dao.timeUntilNextAllowedLogin(session, user)
                dao.logAttempt(session, user)
            }

            currentTime = LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L + 1
            assertNull(dao.timeUntilNextAllowedLogin(session, user))

            repeat(LoginAttemptHibernateDao.LOCKOUT_THRESHOLD) {
                assertNull(dao.timeUntilNextAllowedLogin(session, user))
                dao.logAttempt(session, user)
            }

            dao.logAttempt(session, user)
            assertEquals(
                LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(2).toLong() * 1000L,
                dao.timeUntilNextAllowedLogin(session, user)
            )

            currentTime += LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(2).toLong() * 1000L +
                    LoginAttemptHibernateDao.COOLDOWN_EXPIRY + 1

            assertNull(dao.timeUntilNextAllowedLogin(session, user))

            repeat(LoginAttemptHibernateDao.LOCKOUT_THRESHOLD + 1) {
                dao.timeUntilNextAllowedLogin(session, user)
                dao.logAttempt(session, user)
            }

            assertEquals(
                LoginAttemptHibernateDao.LOCKOUT_DURATION_BASE_SECONDS * 1000L,
                dao.timeUntilNextAllowedLogin(session, user)
            )
        }
    }
}
