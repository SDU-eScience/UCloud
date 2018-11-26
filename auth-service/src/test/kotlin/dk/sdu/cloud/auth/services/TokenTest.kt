package dk.sdu.cloud.auth.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.tokenValidation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenTest {
    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    private val email = "test@testmail.com"
    private val token = "token"
    private val person = personService.createUserByPassword(
        "FirstName Middle",
        "Lastname",
        email,
        Role.ADMIN,
        "ThisIsMyPassword"
    )

    private data class TestContext(
        val tokenService: TokenService<HibernateSession>,
        val userDao: UserDAO<HibernateSession>,
        val refreshTokenDao: RefreshTokenDAO<HibernateSession>,
        val db: DBSessionFactory<HibernateSession>
    )

    private val testJwtVerifier by lazy {
        initializeMicro().tokenValidation as TokenValidationJWT
    }

    private fun createTokenService(): TestContext {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val db = micro.hibernateDatabase
        val testJwtFactory = JWTFactory(testJwtVerifier.algorithm)
        val userDao = UserHibernateDAO(passwordHashingService)
        db.withTransaction {
            try {
                userDao.delete(it, person.id)
            } catch (_: Exception) {
            }

            userDao.insert(it, person)
        }
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val tokenService = TokenService(
            db,
            personService,
            userDao,
            refreshTokenDao,
            testJwtFactory,
            mockk(relaxed = true),
            tokenValidation = testJwtVerifier
        )

        return TestContext(
            tokenService,
            userDao,
            refreshTokenDao,
            db
        )
    }

    @Test
    fun `create and register test`() {
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
                h.put(AttributeURIs.EduPersonTargetedId, listOf("hello"))
                h.put("gn", listOf("Firstname"))
                h.put("sn", listOf("Lastname"))
                h.put("schacHomeOrganization", listOf("SDU"))
                h

            }
            val result = runBlocking { tokenService.processSAMLAuthentication(auth) }
            assertEquals("SDU", result?.organizationId)
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
    fun `request one time token test`() {
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
    fun `refresh test`() {
        with(createTokenService()) {
            db.withTransaction { session ->
                val refreshTAU = RefreshTokenAndUser(email, token, "")
                val refreshHibernateTAU = RefreshTokenHibernateDAO()
                refreshHibernateTAU.insert(session, refreshTAU)

            }
            val result = tokenService.refresh(token)

            val parsedJwt = testJwtVerifier.validate(result.accessToken)
            assertEquals(email, parsedJwt.subject)

            assertTrue(result.accessToken.isNotEmpty())
        }
    }

    @Test(expected = RefreshTokenException.InvalidToken::class)
    fun `refresh test - not valid token`() {
        with(createTokenService()) {
            tokenService.refresh("not a token")
        }
    }

    @Test
    fun `logout test `() {
        with(createTokenService()) {
            db.withTransaction { session ->
                val refreshTAU = RefreshTokenAndUser(email, token, "")
                RefreshTokenHibernateDAO().insert(session, refreshTAU)
            }

            tokenService.logout(token)
        }
    }

    @Test(expected = RefreshTokenException.InvalidToken::class)
    fun `logout test - not valid token`() {
        with(createTokenService()) {
            tokenService.logout("not a token")
        }
    }
}
