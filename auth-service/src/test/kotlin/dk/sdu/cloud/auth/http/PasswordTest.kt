package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
    HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
}


class PasswordTest{

    @Test
    fun `Login test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                       installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
                        }

                        db.withTransaction {
                            userDao.insert(it, PersonUtils.createUserByPassword(
                                "Firstname",
                                "lastname",
                                "user1",
                                Role.ADMIN,
                                "pass1234"
                            ))
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
                        assertTrue(response.headers.allValues().toString().contains("accessToken="))
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
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
                        }

                        db.withTransaction {
                            userDao.insert(it, PersonUtils.createUserByPassword(
                                "Firstname",
                                "lastname",
                                "user1",
                                Role.ADMIN,
                                "pass1234"
                            ))
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
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[6].trim('[', ']')
                        assertEquals("/auth/login?service", result)
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
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
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
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[6].trim('[', ']')
                        assertEquals("/auth/login?service", result)
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
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
                        }

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
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[6].trim('[', ']')
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
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
                        }

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
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[6].trim('[', ']')
                        assertEquals("/auth/login?service", result)

                        val response2 =
                            handleRequest(HttpMethod.Post, "/auth/login?")
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                                setUser(role = Role.ADMIN)
                                setBody("password=pass1234&service=_service")
                            }.response

                        assertEquals(HttpStatusCode.Found, response2.status())
                        val splittedResponse2 = response2.headers.allValues().toString().split('=', ',')
                        val result2 = splittedResponse2[6].trim('[', ']')
                        assertEquals("/auth/login?service", result2)
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
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        routing {
                            PasswordController(
                                db,
                                userDao,
                                tokenService
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/login") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response
                        assertNull(response.content)
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[6].trim('[', ']')
                        assertEquals("/auth/login?invalid", result)

                    }
                )
            }
        }
    }
}