package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.RefreshTokenHibernateDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.utils.testJwtFactory
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
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
        val tokenService: TokenService<HibernateSession>
    )

    private fun Application.createPasswordController(db: HibernateSessionFactory): TestContext {
        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()

        val tokenService = TokenService(
            db,
            userDao,
            refreshTokenDao,
            testJwtFactory,
            mockk(relaxed = true)
        )

        val twoFactorChallengeService = mockk<TwoFactorChallengeService<HibernateSession>>(relaxed = true)
        every { twoFactorChallengeService.isConnected(any()) } returns false
        every { twoFactorChallengeService.createLoginChallengeOrNull(any(), any()) } returns null

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)

        installDefaultFeatures(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            requireJobId = true
        )

        routing {
            PasswordController(
                db,
                userDao,
                loginResponder
            ).configure(this)
        }

        return TestContext(userDao, refreshTokenDao, testJwtFactory, tokenService)
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
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)

                        db.withTransaction {
                            UserHibernateDAO().insert(it, person)
                        }
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `Login test - password wrong`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)

                        db.withTransaction {
                            UserHibernateDAO().insert(it, person)
                        }
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `Login test - user does not exist`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `Login test - no service`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `Login test - missing password or username`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)
                    },

                    test = {
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
                )
            }
        }
    }

    @Test
    fun `Login test - no parameters`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createPasswordController(db)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/login") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response
                        assertNull(response.content)
                        val result = response.headers.values("Location").toString().trim('[', ']')
                        assertEquals("/auth/login?invalid", result)

                    }
                )
            }
        }
    }
}
