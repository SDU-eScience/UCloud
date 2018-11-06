package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.RefreshTokenHibernateDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.service.tokenValidation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordTest {
    private data class TestContext(
        val userDao: UserHibernateDAO,
        val refreshTokenDao: RefreshTokenHibernateDAO,
        val jwtFactory: JWTFactory,
        val tokenService: TokenService<HibernateSession>,
        val db: DBSessionFactory<HibernateSession>,
        val controllers: List<Controller>
    )

    private fun KtorApplicationTestSetupContext.createPasswordController(): TestContext {
        micro.install(HibernateFeature)

        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()

        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val jwtFactory = JWTFactory(tokenValidation.algorithm)

        val tokenService = TokenService(
            micro.hibernateDatabase,
            userDao,
            refreshTokenDao,
            jwtFactory,
            mockk(relaxed = true),
            tokenValidation = tokenValidation
        )

        val twoFactorChallengeService = mockk<TwoFactorChallengeService<HibernateSession>>(relaxed = true)
        every { twoFactorChallengeService.isConnected(any()) } returns false
        every { twoFactorChallengeService.createLoginChallengeOrNull(any(), any()) } returns null

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)

        val controllers = listOf(
            PasswordController(
                micro.hibernateDatabase,
                userDao,
                loginResponder
            )
        )

        return TestContext(userDao, refreshTokenDao, jwtFactory, tokenService, micro.hibernateDatabase, controllers)
    }

    private val person = PersonUtils.createUserByPassword(
        "Firstname",
        "lastname",
        "user1",
        Role.ADMIN,
        "pass1234"
    )

    @Test
    fun `Login test`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    db.withTransaction {
                        UserHibernateDAO().insert(it, person)
                    }

                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("username=user1&password=pass1234&service=_service")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    println(response.headers.allValues())
                    assertTrue(
                        response.headers.values("Location").toString()
                            .contains("/auth/login-redirect?service=_service")
                    )
                    assertTrue(
                        response.headers.values("Location").toString()
                            .contains("accessToken=")
                    )
                    assertTrue(
                        response.headers.values("Location").toString()
                            .contains("refreshToken=")
                    )
                }
            }
        )
    }

    @Test
    fun `Login test - password wrong`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    db.withTransaction {
                        UserHibernateDAO().insert(it, person)
                    }

                    controllers
                }
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("username=user1&password=pass123456&service=_service")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?service=_service&invalid", result)
                }
            }
        )
    }

    @Test
    fun `Login test - user does not exist`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    controllers
                }
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("username=user1&password=pass1234&service=_service")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?service=_service&invalid", result)
                }
            }
        )
    }

    @Test
    fun `Login test - no service`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    controllers
                }
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("username=user1&password=pass1234")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?invalid", result)
                }
            }
        )
    }

    @Test
    fun `Login test - missing password or username`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    controllers
                }
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("username=user1&service=_service")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?service=_service&invalid", result)

                    val response2 =
                        handleRequest(HttpMethod.Post, "/auth/login?")
                        {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("password=pass1234&service=_service")
                        }.response

                    assertEquals(HttpStatusCode.Found, response2.status())
                    val result2 = response2.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?service=_service&invalid", result2)
                }
            }
        )
    }

    @Test
    fun `Login test - no parameters`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    controllers
                }
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/login") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response
                    assertNull(response.content)
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login?invalid", result)
                }
            }
        )
    }
}
