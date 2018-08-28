package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.AuthConfiguration
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class CoreAuthTest{

    private data class TestContext(
        val ottDao: OneTimeTokenHibernateDAO,
        val userdao: UserHibernateDAO,
        val refreshTokenDao: RefreshTokenHibernateDAO,
        val jwtAlg: JWTAlgorithm,
        val config: AuthConfiguration,
        val tokenService: TokenService<HibernateSession>
    )

    private fun Application.createCoreAuthController(db: HibernateSessionFactory,
                                                     enablePassword: Boolean,
                                                     enableWayf: Boolean): TestContext {
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

        routing {
            CoreAuthController(
                db,
                ottDao,
                tokenService,
                enablePassword,
                enableWayf
            ).configure(this)
        }

        return TestContext(ottDao, userDao, refreshTokenDao, jwtAlg, config, tokenService)
    }

    private val person = PersonUtils.createUserByPassword(
        "firstname",
        "lastname",
        "user",
        Role.ADMIN,
        "password"
    )

    @Test
    fun `Login Test - service given, isInvalid True, Wayf false, password true`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        createCoreAuthController(db, true, false)
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
                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        createCoreAuthController(db, false, true)
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
                        createCoreAuthController(db, true, false)
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
                        createCoreAuthController(db, true, false)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login-redirect") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Found, response.status())
                        val result = response.headers.values("Location").toString().trim('[', ']')
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

                        ServiceDAO.insert(Service("_service", "endpointOfService"))
                        createCoreAuthController(db, true, false)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/auth/login-redirect?service=_service") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Found, response.status())
                        val result = response.headers.values("Location").toString().trim('[', ']')
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
                        ServiceDAO.insert(Service("_service", "endpointOfService"))

                        createCoreAuthController(db, true, false)
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
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
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
    fun `Refresh test - unauthorized`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user", ""))
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

    @Test
    fun `Request test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/request?audience=user") {
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
    fun `Request test - missing params`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/request?user") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Claim test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/claim/givenJTI") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.NoContent, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Claim test - Unauthorized not ADMIN or SERVICE`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(
                                session, PersonUtils.createUserByPassword(
                                    "firstname",
                                    "lastname",
                                    "user",
                                    Role.USER,
                                    "password"
                                )
                            )
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/USER", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/claim/givenJTI") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.USER)
                            }.response

                        assertEquals(HttpStatusCode.Forbidden, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Claim test - claim same`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/claim/givenJTI") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.NoContent, response.status())

                        val response2 =
                            handleRequest(HttpMethod.Post, "/auth/claim/givenJTI") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Conflict, response2.status())
                    }
                )
            }
        }
    }

    @Test
    fun `Logout Test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createCoreAuthController(db, true, false)

                        db.withTransaction { session ->
                            UserHibernateDAO().insert(session, person)
                            RefreshTokenHibernateDAO().insert(session, RefreshTokenAndUser("user", "user/ADMIN", ""))
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/logout") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.NoContent, response.status())
                    }
                )
            }
        }
    }
}