package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserHibernateDAOTest{

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `insert, find and delete`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    "testmail.com",
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                val userHibernate = UserHibernateDAO()
                userHibernate.insert(session, person)
                assertEquals("testmail.com", userHibernate.findById(session, "testmail.com").id)
                userHibernate.delete(session, "testmail.com")
                assertNull(userHibernate.findByIdOrNull(session, "testmail.com"))
            }
        }
    }

    @Test
    fun `insert 2 and list all`() {
        withDatabase { db ->
            db.withTransaction { session ->
                val person = PersonUtils.createUserByPassword(
                    "FirstName Middle",
                    "Lastname",
                    "testmail.com",
                    Role.ADMIN,
                    "ThisIsMyPassword"
                )
                val person2 = PersonUtils.createUserByPassword(
                    "McFirstName McMiddle",
                    "McLastname",
                    "anotherMail.com",
                    Role.ADMIN,
                    "Password1234"
                )
                val userHibernate = UserHibernateDAO()
                userHibernate.insert(session, person)
                userHibernate.insert(session, person2)

                val listOfAll = userHibernate.listAll(session)

                assertEquals(2, listOfAll.size)
                assertEquals("testmail.com", listOfAll[0].id)
                assertEquals("anotherMail.com", listOfAll[1].id)

            }
        }
    }

    @Test (expected = UserException.NotFound::class)
    fun `delete non existing user`() {
        withDatabase { db ->
            val session = db.openSession()
            val userHibernate = UserHibernateDAO()
            userHibernate.delete(session, "testmail.com")
        }
    }

}