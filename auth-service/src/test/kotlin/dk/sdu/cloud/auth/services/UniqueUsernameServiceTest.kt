package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.Ignore

class UniqueUsernameServiceTest {
    private lateinit var service: UniqueUsernameService<HibernateSession>
    private lateinit var userDao: UserDAO<HibernateSession>
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var personService: PersonService
    private lateinit var personTemplate: Person.ByPassword

    @BeforeTest
    fun initTests() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val passwordHashingService = PasswordHashingService()
        db = micro.hibernateDatabase
        userDao = UserHibernateDAO(passwordHashingService)
        service = UniqueUsernameService(db, userDao)
        personService = PersonService(passwordHashingService, service)

        personTemplate = personService.createUserByPassword(
            "Dan",
            "Thrane",
            "dthrane@foo.dkl",
            Role.ADMIN,
            "password"
        )
    }

    @Test
    fun `generate a single username`() {
        val prefix = "DanThrane"
        val id = service.generateUniqueName(prefix)
        db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
        assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
    }

    @Test
    fun `generate 1000 usernames`() {
        val prefix = "DanThrane"
        repeat(1000) {
            val id = service.generateUniqueName(prefix)
            println(id)
            db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
            assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
        }
    }

    @Ignore
    @Test
    fun `generate 11000 usernames`() {
        val prefix = "DanThrane"
        repeat(11000) {
            val id = service.generateUniqueName(prefix)
            println(id)
            db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
            assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
        }
    }
}
