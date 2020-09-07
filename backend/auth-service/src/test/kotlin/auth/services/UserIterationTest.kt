package dk.sdu.cloud.auth.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.assertThatProperty
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserIterationTest {
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
        val twoFactDao = TwoFactorAsyncDAO()
        val userDao = UserAsyncDAO(passwordHashingService, twoFactDao)
        val uniqueNameService = UniqueUsernameService(db, userDao)
        val personService = PersonService(passwordHashingService, uniqueNameService)
        cursorDao = CursorStateAsyncDao()
        val rpc = mockk<RpcClient>()
        val tokenVal = mockk<TokenValidation<DecodedJWT>>()
        val refresh = RefreshingJWTAuthenticator(rpc, "refreshToken", tokenVal)
        iterationService = UserIterationService(
            "localhost",
            1234,
            db,
            cursorDao,
            rpc,
            refresh
        )

        val prefix = "UserName"
        val personTemplate = personService.createUserByPassword(
            "User",
            "Name",
            "name@foo.dkl",
            Role.ADMIN,
            "password",
            "name@foo.dkl"
        )

        runBlocking {
            repeat(1010) {
                val id = uniqueNameService.generateUniqueName(prefix)
                db.withSession { userDao.insert(it, personTemplate.copy(id = id)) }
                assertThatProperty(id, { it }) { id.startsWith(prefix + UniqueUsernameService.SEPARATOR) }
            }
        }
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    lateinit var iterationService: UserIterationService

    @Test
    fun `test`() {
        runBlocking {
            val cursor = iterationService.create()
            val firstFetch = iterationService.fetchNext(cursor)
            assertEquals(1000, firstFetch.size)
            val secondFetch = iterationService.fetchNext(cursor)
            assertEquals(10, secondFetch.size)
            iterationService.close(cursor)
        }
    }

    lateinit var cursorDao: CursorStateAsyncDao
    @Test
    fun `Iteration Dao test`() {
        runBlocking {
            val id = "cursorID"
            val state = CursorState(id, "hostname", 1234, Time.now() + 1000L * 60 * 30)
            cursorDao.create(db, state)
            val found = cursorDao.findByIdOrNull(db, id)
            assertNotNull(found)
            val notReal = cursorDao.findByIdOrNull(db, "notACursor")
            assertNull(notReal)
            val newExpire = Time.now() + 1000L * 60 * 30 + 5000
            cursorDao.updateExpiresAt(db, id, newExpire)
            val found2 = cursorDao.findByIdOrNull(db, id)
            assertNotNull(found2)
            assertTrue(found.expiresAt < found2.expiresAt)

        }
    }
}
