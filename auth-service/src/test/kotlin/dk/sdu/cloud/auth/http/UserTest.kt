package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.hibernate.Session
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = dk.sdu.cloud.auth.api.Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer $username/$role")
}

fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    install(JWTProtection)

    routing {
        protect()
        configureControllers(*controllers)
    }
}

private fun Application.configureAuthServer(userDao: UserHibernateDAO,
                                            hsfactory: HibernateSessionFactory = mockk(relaxed = true),
                                            userCreationService: UserCreationService<Session> = mockk(relaxed = true)) {
    @Suppress("UNCHECKED_CAST")
    configureBaseServer(
        UserController(
            hsfactory,
            userDao,
            userCreationService,
            mockk(relaxed = true)
        )
    )
}

class UserTest {

    @Test
    fun `create user and lookup`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db,userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/register") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "username":"User1",
                                    "password":"ThisIsPassword",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())


                        val response2 =
                            handleRequest(HttpMethod.Post, "/auth/users/lookup") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "users":
                                    [
                                        "User1",
                                        "User2"
                                    ]
                                }
                            """.trimIndent()
                                )
                            }.response

                        println(response2.content)

                        assertEquals(HttpStatusCode.OK, response2.status())

                    }
                )
            }
        }
    }

    @Test
    fun `create user - not admin`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/register") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                                setBody(
                                    """
                                {
                                    "username":"User1",
                                    "password":"ThisIsPassword",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `create user - wrong JSON`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/register") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "username":
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `change password`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/register") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "username":"user",
                                    "password":"ThisIsPassword",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val response2 =
                            handleRequest(HttpMethod.Post, "/auth/users/password") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "currentPassword":"ThisIsPassword",
                                    "newPassword":"Pass",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response2.status())

                    }
                )
            }
        }
    }

    @Test
    fun `change password - wrong original password`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/register") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "username":"user",
                                    "password":"ThisIsPassword",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val response2 =
                            handleRequest(HttpMethod.Post, "/auth/users/password") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "currentPassword":"ThisPassword",
                                    "newPassword":"Pass",
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response2.status())

                    }
                )
            }
        }
    }

    @Test
    fun `change password - wrong JSON`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/password") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "currentPassword":
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())

                    }
                )
            }
        }
    }


    @Test
    fun `lookup users - not admin`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/lookup") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                                setBody(
                                    """
                                {
                                    "users":
                                    [
                                        "user1",
                                        "user2",
                                        "user3"
                                    ]
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `lookup users - wrong JSON`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val userDao = UserHibernateDAO()
                        val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                        configureAuthServer(userDao, db, userCreationService)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/auth/users/lookup") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody(
                                    """
                                {
                                    "users":
                                    [
                                        "use
                                    ]
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())

                    }
                )
            }
        }
    }
}
