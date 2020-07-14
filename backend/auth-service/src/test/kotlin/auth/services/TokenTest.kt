package dk.sdu.cloud.auth.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenTest {
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

    private val email = "test@testmail.com"
    private val token = "token"
    private lateinit var person: Person

    private data class TestContext(
        val tokenService: TokenService,
        val userDao: UserAsyncDAO,
        val refreshTokenDao: RefreshTokenAsyncDAO,
        val db: AsyncDBSessionFactory,
        val personService: PersonService,
        val passwordHashingService: PasswordHashingService
    )

    private val testJwtVerifier by lazy {
        initializeMicro().tokenValidation as TokenValidationJWT
    }

    private fun createTokenService(): TestContext = runBlocking {
        val micro = initializeMicro()
        val testJwtFactory = JWTFactory(testJwtVerifier.algorithm)
        val passwordHashingService = PasswordHashingService()
        val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
        val personService = PersonService(passwordHashingService, UniqueUsernameService(db, userDao))

        person = personService.createUserByPassword(
            "FirstName Middle",
            "Lastname",
            email,
            Role.ADMIN,
            "ThisIsMyPassword",
            email
        )

        userDao.insert(db, person)

        val refreshTokenDao = RefreshTokenAsyncDAO()
        val tokenService = TokenService(
            db,
            personService,
            userDao,
            refreshTokenDao,
            testJwtFactory,
            UserCreationService(db, userDao, micro.eventStreamService.createProducer(AuthStreams.UserUpdateStream)),
            tokenValidation = testJwtVerifier
        )

        TestContext(
            tokenService,
            userDao,
            refreshTokenDao,
            db,
            personService,
            passwordHashingService
        )
    }

    @Test
    fun `create and register test`(): Unit = runBlocking {
        with(createTokenService()) {
            val result = tokenService.createAndRegisterTokenFor(person)
            val parsedJwt = testJwtVerifier.validate(result.accessToken)
            assertEquals(email, parsedJwt.subject)

            assertTrue(result.refreshToken.isNotEmpty())
        }
    }

    @Test
    fun `process SAML auth test - User not found in system, create new WAYF `() {
        with(createTokenService()) {
            val auth = mockk<SamlRequestProcessor>()
            every { auth.authenticated } returns true
            every { auth.attributes } answers {
                val h = HashMap<String, List<String>>(10)
                h.put("eduPersonTargetedID", listOf("hello"))
                h.put("gn", listOf("Firstname"))
                h.put("sn", listOf("Lastname"))
                h.put("schacHomeOrganization", listOf("sdu.dk"))
                h

            }
            val result = runBlocking { tokenService.processSAMLAuthentication(auth) }
            assertEquals("sdu.dk", result?.organizationId)
            assertEquals("Firstname", result?.firstNames)
            assertEquals("Lastname", result?.lastName)
        }
    }

    @Test
    fun `process SAML auth test - missing EDU Person Target ID`() {
        with(createTokenService()) {
            val auth = mockk<SamlRequestProcessor>()
            every { auth.authenticated } returns true
            every { auth.attributes } answers { hashMapOf() }
            runBlocking { assertNull(tokenService.processSAMLAuthentication(auth)) }
        }
    }

    @Test
    fun `process SAML auth test - not authenticated`() {
        with(createTokenService()) {
            val auth = mockk<SamlRequestProcessor>()
            every { auth.authenticated } returns false
            runBlocking { assertNull(tokenService.processSAMLAuthentication(auth)) }
        }
    }

    @Test
    fun `request one time token test`(): Unit = runBlocking {
        val user = "test@testmail.com"
        val jwt = TokenValidationMock.createTokenForUser(user, Role.USER)
        with(createTokenService()) {
            val result = tokenService.requestOneTimeToken(
                jwt,
                listOf(SecurityScope.construct(listOf("a", "b", "c"), AccessRight.READ_WRITE))
            )

            val parsedJwt = testJwtVerifier.validate(result.accessToken)
            assertEquals(user, parsedJwt.subject)

            assertTrue(result.jti.isNotEmpty())
        }
    }

    @Test
    fun `refresh test`(): Unit = runBlocking {
        with(createTokenService()) {
            db.withTransaction { session ->
                val refreshTAU = RefreshTokenAndUser(email, token, "")
                val refreshDao = RefreshTokenAsyncDAO()
                refreshDao.insert(session, refreshTAU)

            }
            val result = tokenService.refresh(token)

            val parsedJwt = testJwtVerifier.validate(result.accessToken)
            assertEquals(email, parsedJwt.subject)

            assertTrue(result.accessToken.isNotEmpty())
        }
    }

    @Test(expected = RefreshTokenException.InvalidToken::class)
    fun `refresh test - not valid token`(): Unit = runBlocking {
        with(createTokenService()) {
            tokenService.refresh("not a token")
            Unit
        }
    }

    @Test
    fun `logout test `(): Unit = runBlocking {
        with(createTokenService()) {
            db.withTransaction { session ->
                val refreshTAU = RefreshTokenAndUser(email, token, "")
                RefreshTokenAsyncDAO().insert(session, refreshTAU)
            }

            tokenService.logout(token)
        }
    }

    @Test(expected = RefreshTokenException.InvalidToken::class)
    fun `logout test - not valid token`(): Unit = runBlocking {
        with(createTokenService()) {
            tokenService.logout("not a token")
        }
    }
}
