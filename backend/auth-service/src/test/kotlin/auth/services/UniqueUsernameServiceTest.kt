package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.assertThatProperty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore

class UniqueUsernameServiceTest {
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

        val passwordHashingService = PasswordHashingService()
        userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
        service = UniqueUsernameService(db, userDao)
        personService = PersonService(passwordHashingService, service)

        personTemplate = personService.createUserByPassword(
            "Dan",
            "Thrane",
            "dthrane@foo.dkl",
            Role.ADMIN,
            "password",
            "dthrane@foo.dkl"
        )
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    private lateinit var service: UniqueUsernameService
    private lateinit var userDao: UserAsyncDAO
    private lateinit var personService: PersonService
    private lateinit var personTemplate: Person.ByPassword


    @Test
    fun `generate a single username`(): Unit = runBlocking {
        val prefix = "DanThrane"
        val id = service.generateUniqueName(prefix)
        db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
        assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
    }

    @Test
    fun `generate 1000 usernames`(): Unit = runBlocking {
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
    fun `generate 11000 usernames`(): Unit = runBlocking {
        val prefix = "DanThrane"
        repeat(11000) {
            val id = service.generateUniqueName(prefix)
            println(id)
            db.withTransaction { userDao.insert(it, personTemplate.copy(id = id)) }
            assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
        }
    }
}
