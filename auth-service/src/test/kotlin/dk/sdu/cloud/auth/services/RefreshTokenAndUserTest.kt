package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.withDatabase
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshTokenAndUserTest {
    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    @Test
    fun `insert, find and delete`() {
        withDatabase { db ->
            runBlocking {
                val email = "test@testmail.com"
                val token = "tokenToGive"

                db.withTransaction { session ->
                    val person = personService.createUserByPassword(
                        "FirstName Middle",
                        "Lastname",
                        email,
                        Role.ADMIN,
                        "ThisIsMyPassword",
                        email
                    )
                    UserHibernateDAO(passwordHashingService, TwoFactorHibernateDAO()).insert(session, person)
                    val refreshTAU = RefreshTokenAndUser(email, token, "")
                    val refreshHibernateTAU = RefreshTokenHibernateDAO()

                    refreshHibernateTAU.insert(session, refreshTAU)

                    assertEquals(email, refreshHibernateTAU.findById(session, token)?.associatedUser)
                    assertTrue(refreshHibernateTAU.delete(session, token))
                    assertNull(refreshHibernateTAU.findById(session, token))
                }
            }
        }
    }
    @Test
    fun `insert and delete expired`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = RefreshTokenHibernateDAO()
        val email = "test@testmail.com"
        val token = "tokenToGive"

        runBlocking {
            db.withTransaction { session ->
                val person = personService.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword",
                    email
                )
                UserHibernateDAO(passwordHashingService, TwoFactorHibernateDAO()).insert(session, person)
                dao.insert(session, RefreshTokenAndUser(
                    email, "token", "csrf", refreshTokenExpiry = (System.currentTimeMillis() + 1000000))
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
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val dao = RefreshTokenHibernateDAO()
        val email = "test@testmail.com"

        runBlocking {
            db.withTransaction { session ->
                val person = personService.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword",
                    email
                )
                UserHibernateDAO(passwordHashingService, TwoFactorHibernateDAO()).insert(session, person)
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
                assertEquals(2, foundSessions.itemsInTotal)
                assertEquals("token", foundSessions.items.first().token)
                assertEquals("token2", foundSessions.items.last().token)
            }
        }
    }



    @Test(expected = UserException.NotFound::class)
    fun `insert - user not found`() {
        withDatabase { db ->
            runBlocking {
                db.withTransaction { session ->
                    val refreshTAU = RefreshTokenAndUser("non existing User", "token", "")
                    val refreshHibernateTAU = RefreshTokenHibernateDAO()
                    refreshHibernateTAU.insert(session, refreshTAU)
                }
            }
        }
    }

    @Test
    fun `delete - does not exist`() {
        withDatabase { db ->
            runBlocking {
                db.withTransaction { session ->
                    assertFalse(RefreshTokenHibernateDAO().delete(session, "token"))
                }
            }
        }
    }
}
