package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshTokenAndUserTest{

    @Test
    fun `insert, find and delete`() {
        withDatabase { db ->

            val email = "test@testmail.com"
            val token = "tokenToGive"

            db.withTransaction { session ->
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    email,
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                UserHibernateDAO().insert(session, person)
                val refreshTAU = RefreshTokenAndUser(email, token)
                val refreshHibernateTAU = RefreshTokenHibernateDAO()

                refreshHibernateTAU.insert(session, refreshTAU)

                assertEquals(email, refreshHibernateTAU.findById(session, token)?.associatedUser)
                assertTrue(refreshHibernateTAU.delete(session, token))
                assertNull(refreshHibernateTAU.findById(session, token))
            }
        }
    }

    @Test (expected = UserException.NotFound::class)
    fun `insert - user not found`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val refreshTAU = RefreshTokenAndUser("non existing User", "token")
                val refreshHibernateTAU = RefreshTokenHibernateDAO()
                refreshHibernateTAU.insert(session, refreshTAU)
            }
        }
    }

    @Test
    fun `delete - does not exist`() {
        withDatabase { db ->
            db.withTransaction { session ->
                assertFalse(RefreshTokenHibernateDAO().delete(session, "token"))
            }
        }
    }

}