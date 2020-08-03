package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshTokenAndUserTest {
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

    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    val email = "test@testmail.com"
    val token = "tokenToGive"

    val person = personService.createUserByPassword(
        "FirstName Middle",
        "Lastname",
        email,
        Role.ADMIN,
        "ThisIsMyPassword",
        email
    )

    @Test
    fun `insert, find and delete`() {
        runBlocking {
            db.withTransaction { session ->
                UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO()).insert(session, person)
                val refreshTAU = RefreshTokenAndUser(email, token, "")
                val refreshDao = RefreshTokenAsyncDAO()

                refreshDao.insert(session, refreshTAU)

                assertEquals(email, refreshDao.findById(session, token)?.associatedUser)
                assertTrue(refreshDao.delete(session, token))
                assertNull(refreshDao.findById(session, token))
            }
        }
    }

    @Test
    fun `insert and delete expired`() {
        val dao = RefreshTokenAsyncDAO()
        runBlocking {
            db.withTransaction { session ->
                UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO()).insert(session, person)
                dao.insert(session, RefreshTokenAndUser(
                    email, "token", "csrf", refreshTokenExpiry = (Time.now() + 1000000))
                )
                dao.insert(session, RefreshTokenAndUser(email, "token2", "csrf", refreshTokenExpiry = 1549209140000))
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val foundSessions = dao.findUserSessions(session, email, NormalizedPaginationRequest(10,0) )
                assertEquals(2, foundSessions.itemsInTotal)
                assertEquals("token", foundSessions.items.first().token)
                assertEquals("token2", foundSessions.items.last().token)
            }
        }

        runBlocking {
            db.withTransaction { session ->
                dao.deleteExpired(session)
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val foundSessions = dao.findUserSessions(session, email, NormalizedPaginationRequest(10, 0))
                assertEquals(1, foundSessions.itemsInTotal)
                assertEquals("token", foundSessions.items.first().token)
            }
        }
    }

    @Test
    fun `insert and delete expired (both null)`() {
        val dao = RefreshTokenAsyncDAO()
        runBlocking {
            db.withTransaction { session ->
                UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO()).insert(session, person)
                dao.insert(session, RefreshTokenAndUser(email, "token", "csrf"))
                dao.insert(session, RefreshTokenAndUser(email, "token2", "csrf"))
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val foundSessions = dao.findUserSessions(session, email, NormalizedPaginationRequest(10,0) )
                assertEquals(2, foundSessions.itemsInTotal)
                assertEquals("token", foundSessions.items.first().token)
                assertEquals("token2", foundSessions.items.last().token)
                val findToken = dao.findTokenForUser(db, email)
                assertNotNull(findToken)
                assertEquals("token", findToken.token)
            }
        }

        runBlocking {
            val found = dao.findById(db, "token")
            assertNotNull(found)
            assertEquals("csrf", found.csrf)
            dao.updateCsrf(db, "token", "newCsrf")
            val foundAfterUpdate = dao.findById(db, "token")
            assertNotNull(foundAfterUpdate)
            assertEquals("newCsrf", foundAfterUpdate.csrf)
        }

        runBlocking {
            db.withTransaction { session ->
                dao.deleteExpired(session)
            }
        }

        runBlocking {
            db.withTransaction { session ->
                val foundSessions = dao.findUserSessions(session, email, NormalizedPaginationRequest(10, 0))
                assertEquals(2, foundSessions.itemsInTotal)
                assertEquals("token2", foundSessions.items.first().token)
                assertEquals("token", foundSessions.items.last().token)
                dao.invalidateUserSessions(db, email)
                val foundSessionsAfterInvalid = dao.findUserSessions(session, email, NormalizedPaginationRequest(10, 0))
                assertEquals(0, foundSessionsAfterInvalid.itemsInTotal)

            }
        }
    }



    @Test(expected = UserException.NotFound::class)
    fun `insert - user not found`() {
            runBlocking {
                db.withTransaction { session ->
                    val refreshTAU = RefreshTokenAndUser("non existing User", "token", "")
                    val refreshHibernateTAU = RefreshTokenAsyncDAO()
                    refreshHibernateTAU.insert(session, refreshTAU)
                }
            }

    }

    @Test
    fun `delete - does not exist`() {
        runBlocking {
            db.withSession { session ->
                assertFalse(RefreshTokenAsyncDAO().delete(session, "token"))
            }
        }
    }

}
