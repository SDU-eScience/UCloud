package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.AuthConfiguration
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.install
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

class CoreAuthTest{

    private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
        HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
    }

    @Test
    fun `Login Test - service given, isInvalid True, Wayf false, password true`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login?service=_service&invalid=true") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Login Test - service given, isInvalid True, Wayf true, password false`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns false
                        every { config.enableWayf } returns true

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login?service=_service&invalid=true") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Login test - no service given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
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

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Redirect login test - no service given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
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

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login-redirect") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Found, response.status())
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[8].trim('[', ']')
                        assertEquals("/auth/login", result)                    }
                )
            }
        }
    }

    @Test
    fun `Redirect login test - service given, no accesstoken given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login-redirect?service=_service") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Found, response.status())
                        val splittedResponse = response.headers.allValues().toString().split('=', ',')
                        val result = splittedResponse[8].trim('[', ']')
                        assertEquals("/auth/login?invalid&service", result)                    }
                )
            }
        }
    }

    @Test
    fun `Redirect login test - service given, accesstoken given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login-redirect?service=_service&accessToken=access&refreshToken=rtoken") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Refresh test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        db.withTransaction { session ->
                            userDao.insert(session, PersonUtils.createUserByPassword(
                                "firstname",
                                "lastname",
                                "user",
                                Role.ADMIN,
                                "password"
                            ))
                            refreshTokenDao.insert(session, RefreshTokenAndUser("user", "user/ADMIN"))
                        }
                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/refresh") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Refresh test - unautherized`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val ottDao = OneTimeTokenHibernateDAO()
                        val userDao = UserHibernateDAO()
                        val refreshTokenDao = RefreshTokenHibernateDAO()
                        val jwtAlg = JWTAlgorithm.HMAC256("foobar")
                        val config = mockk<AuthConfiguration>()
                        val tokenService = TokenService(
                            db,
                            userDao,
                            refreshTokenDao,
                            jwtAlg,
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true)
                        )

                        db.withTransaction { session ->
                            userDao.insert(session, PersonUtils.createUserByPassword(
                                "firstname",
                                "lastname",
                                "user",
                                Role.ADMIN,
                                "password"
                            ))
                            refreshTokenDao.insert(session, RefreshTokenAndUser("user", "user"))
                        }
                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            requireJobId = true
                        )

                        install(JWTProtection)

                        every { config.enablePasswords } returns true
                        every { config.enableWayf } returns false

                        routing {
                            CoreAuthController(
                                db,
                                ottDao,
                                tokenService,
                                config.enablePasswords,
                                config.enableWayf
                            ).configure(this)
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/refresh") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, response.status())
                    }
                )
            }
        }
    }
}