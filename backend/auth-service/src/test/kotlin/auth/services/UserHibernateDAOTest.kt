package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserHibernateDAOTest {
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

        passwordHashingService = PasswordHashingService()
        userHibernate = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
        personService = PersonService(passwordHashingService, UniqueUsernameService(db, userHibernate))

        person = personService.createUserByPassword(
            "FirstName Middle",
            "Lastname",
            email,
            Role.ADMIN,
            "ThisIsMyPassword",
            email
        )

        person2 = personService.createUserByPassword(
            "McFirstName McMiddle",
            "McLastname",
            email2,
            Role.USER,
            "Password1234",
            email2
        ).copy(uid = 1)
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    private lateinit var passwordHashingService: PasswordHashingService
    private lateinit var personService: PersonService
    private lateinit var userHibernate: UserAsyncDAO

    private val email = "test@testmail.com"
    private lateinit var person: Person
    private val email2 = "anotherEmail@test.com"
    private lateinit var person2: Person

    @Test
    fun `insert, find and delete`(): Unit = runBlocking {
        db.withTransaction { session ->
            val userHibernate = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
            userHibernate.insert(session, person)
            assertEquals(email, userHibernate.findById(session, email).id)
            userHibernate.delete(session, email)
            assertNull(userHibernate.findByIdOrNull(session, email))
        }
    }

    @Test
    fun `insert 2 with same email`(): Unit = runBlocking {
        db.withTransaction { session ->
            val userHibernate = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
            userHibernate.insert(session, person)
            try {
                userHibernate.insert(session, person)
            } catch (ex: RPCException) {
                assertEquals(HttpStatusCode.Conflict, ex.httpStatusCode)
            }

        }
    }

    @Test(expected = UserException.NotFound::class)
    fun `delete non existing user`(): Unit = runBlocking {
        val session = db.openSession()
        val userHibernate = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
        userHibernate.delete(session, "test@testmail.com")
    }

    @Test
    fun `insert WAYF`(): Unit = runBlocking {
        val auth = mockk<SamlRequestProcessor>()
        val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
        every { auth.authenticated } returns true
        every { auth.attributes } answers {
            val h = HashMap<String, List<String>>(10)
            h.put("eduPersonTargetedID", listOf("hello"))
            h.put("gn", listOf("Firstname"))
            h.put("sn", listOf("Lastname"))
            h.put("schacHomeOrganization", listOf("sdu.dk"))
            h
        }

        val person = personService.createUserByWAYF(auth)
        db.withTransaction { session ->
            userDao.insert(session, person)
        }
        assertEquals("sdu.dk", person.organizationId)
    }

    @Test
    fun `toggle emails`() {
        runBlocking {
            db.withTransaction { session ->
                userHibernate.insert(session, person)

                assertTrue(userHibernate.wantEmails(session, person.id))

                userHibernate.toggleEmail(session, person.id)

                assertFalse(userHibernate.wantEmails(session, person.id))

                userHibernate.toggleEmail(session, person.id)
            }
        }
    }

    @Test
    fun `findBY test`() {
        runBlocking {
            db.withTransaction { session ->
                userHibernate.insert(session, person)
                userHibernate.insert(session, person2)
            }
            db.withSession {
                val foundById = userHibernate.findAllByIds(db, listOf(person.id, person2.id))
                assertEquals(2, foundById.size)
                val foundPerson = foundById[person.id]
                foundPerson as Person.ByPassword
                assertEquals(0, foundPerson.serviceLicenseAgreement)

                userHibernate.setAcceptedSlaVersion(db, person.id, 4)
                val foundById2 = userHibernate.findAllByIds(db, listOf(person.id))
                assertEquals(1, foundById2.size)
                val foundPerson2 = foundById2[person.id] as Person.ByPassword
                assertEquals(4, foundPerson2.serviceLicenseAgreement)

                val foundByEmail = userHibernate.findByEmail(db, email)
                assertEquals(person.id, foundByEmail.userId)

                val foundInfo = userHibernate.getUserInfo(db, person.id)
                assertEquals(person.firstNames, foundInfo.firstNames)

                userHibernate.updateUserInfo(db, person.id, "newName", "newLast", "newEmail")
                val foundAfterUpdate = userHibernate.getUserInfo(db, person.id)
                assertEquals("newEmail", foundAfterUpdate.email)
                assertEquals("newName", foundAfterUpdate.firstNames)
                assertEquals("newLast", foundAfterUpdate.lastName)

                val foundByUid = userHibernate.findAllByUIDs(db, listOf(person.uid, person2.uid))
                assertEquals(2, foundByUid.size)
                assertEquals("newName", (foundByUid[person.uid] as Person).firstNames)
            }
        }
    }

    @Test
    fun `Update Password test`() {
        runBlocking {
            db.withTransaction { session ->
                userHibernate.insert(session, person)
            }
            db.withSession {
                val foundById = userHibernate.findAllByIds(db, listOf(person.id))
                assertEquals(1, foundById.size)
                val foundPerson = foundById[person.id]
                foundPerson as Person.ByPassword

                userHibernate.updatePassword(db, person.id, "NEWPass", true, "ThisIsMyPassword")

                val found = userHibernate.findById(db, person.id) as Person.ByPassword
                assertNotEquals(foundPerson.password, found.password)

                userHibernate.updatePassword(db, person.id, "IDidItAgain", false, null)

                val foundAgain = userHibernate.findById(db, person.id) as Person.ByPassword
                assertNotEquals(found.password, foundAgain.password)
            }
        }
    }


}
