package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import org.hibernate.NonUniqueObjectException
import org.junit.Test
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserHibernateDAOTest {
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var passwordHashingService: PasswordHashingService
    private lateinit var personService: PersonService
    private lateinit var userHibernate: UserHibernateDAO

    private val email = "test@testmail.com"
    private lateinit var person: Person
    private val email2 = "anotherEmail@test.com"
    private lateinit var person2: Person

    @BeforeTest
    fun initTests() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        db = micro.hibernateDatabase
        passwordHashingService = PasswordHashingService()
        userHibernate = UserHibernateDAO(passwordHashingService)
        personService = PersonService(passwordHashingService, UniqueUsernameService(db, userHibernate))

        person = personService.createUserByPassword(
            "FirstName Middle",
            "Lastname",
            email,
            Role.ADMIN,
            "ThisIsMyPassword"
        )

        person2 = personService.createUserByPassword(
            "McFirstName McMiddle",
            "McLastname",
            email2,
            Role.USER,
            "Password1234"
        )
    }

    @Test
    fun `insert, find and delete`() {
        db.withTransaction { session ->
            val userHibernate = UserHibernateDAO(passwordHashingService)
            userHibernate.insert(session, person)
            assertEquals(email, userHibernate.findById(session, email).id)
            userHibernate.delete(session, email)
            assertNull(userHibernate.findByIdOrNull(session, email))
        }
    }

    @Test
    fun `insert 2 and list all`() {
        db.withTransaction { session ->
            val userHibernate = UserHibernateDAO(passwordHashingService)
            userHibernate.insert(session, person)
            userHibernate.insert(session, person2)

            val listOfAll = userHibernate.listAll(session)

            assertEquals(2, listOfAll.size)
            assertEquals(email, listOfAll[0].id)
            assertEquals(email2, listOfAll[1].id)

        }
    }

    @Test(expected = NonUniqueObjectException::class)
    fun `insert 2 with same email`() {
        db.withTransaction { session ->
            val userHibernate = UserHibernateDAO(passwordHashingService)
            userHibernate.insert(session, person)
            userHibernate.insert(session, person)

        }
    }

    @Test(expected = UserException.NotFound::class)
    fun `delete non existing user`() {
        val session = db.openSession()
        val userHibernate = UserHibernateDAO(passwordHashingService)
        userHibernate.delete(session, "test@testmail.com")
    }

    @Test
    fun `insert WAYF`() {
        val auth = mockk<SamlRequestProcessor>()
        val userDao = UserHibernateDAO(passwordHashingService)
        every { auth.authenticated } returns true
        every { auth.attributes } answers {
            val h = HashMap<String, List<String>>(10)
            h.put(AttributeURIs.EduPersonTargetedId, listOf("hello"))
            h.put("gn", listOf("Firstname"))
            h.put("sn", listOf("Lastname"))
            h.put("schacHomeOrganization", listOf("SDU"))
            h
        }

        val person = personService.createUserByWAYF(auth)
        db.withTransaction { session ->
            userDao.insert(session, person)
        }
        assertEquals("SDU", person.organizationId)
    }

    @Test
    fun `Create Service Entity`() {
        val date = Date()
        val entity = ServiceEntity("_id", Role.SERVICE, date, date)
        assertEquals(date, entity.createdAt)
        assertEquals(date, entity.modifiedAt)

        val principal = entity.toModel()
        assertEquals(ServicePrincipal("_id", Role.SERVICE), principal)
        val backToEntity = principal.toEntity()
        assertEquals(backToEntity.id, entity.id)
        assertEquals(backToEntity.role, entity.role)
    }

    @Test
    fun `Create Person Entity and use hashcode and equals test`() {
        val date = Date()
        val person1 = PersonEntityByPassword(
            "id",
            Role.USER,
            date,
            date,
            "title",
            "firstname",
            "lastname",
            "phone",
            "orcid",
            hashedPassword = ByteArray(2),
            salt = ByteArray(4)
        )
        val person2 = PersonEntityByPassword(
            "id",
            Role.USER,
            date,
            date,
            "title",
            "firstname",
            "lastname",
            "phone",
            "orcid",
            hashedPassword = ByteArray(2),
            salt = ByteArray(4)
        )

        assertTrue(person1.equals(person2))

        val hashedPerson = person1.hashCode()
        val hashedPerson2 = person2.hashCode()
        //Same values, so should be same hash
        assertEquals(hashedPerson, hashedPerson2)
    }

    @Test
    fun `create Person by WAYF Entity and transform to model`() {
        val entity = PersonEntityByWAYF(
            "id",
            Role.USER,
            Date(),
            Date(),
            "title",
            "Firstname",
            "lastname",
            "phone",
            "orcid",
            orgId = "orgid",
            wayfId = "wayfid"
        )
        val model = entity.toModel()
        val backToEntity = model.toEntity()

        assertEquals(entity.id, backToEntity.id)
    }
}
