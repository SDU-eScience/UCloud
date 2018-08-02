package dk.sdu.cloud.auth.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.UserEventProducer
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.FakeDBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
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
import io.mockk.every
import io.mockk.mockk
import org.hibernate.SessionFactory
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

private fun Application.configureAuthServer(userDao:UserHibernateDAO) {
    @Suppress("UNCHECKED_CAST")
    configureBaseServer(
        UserController(
            mockk(relaxed = true),
            userDao,
            mockk(relaxed = true)
        )
    )
}

class UserTest {

    @Test
    fun `create user`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val userDao = UserHibernateDAO()
                    configureAuthServer(userDao)
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

                }
            )
        }
    }

  /*  @Test
    fun `create user - not admin`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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

    @Test
    fun `create user - wrong JSON`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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

    @Test
    fun `change password`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/users/password") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody(
                                """
                                {
                                    "currentPassword":"User1",
                                    "newPassword":"ThisIsPassword",
                                }
                            """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun `change password - wrong JSON`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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

    @Test
    fun `lookup users`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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
                                        "user1",
                                        "user2",
                                        "user3"
                                    ]
                                }
                            """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun `lookup users - not admin`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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

    @Test
    fun `lookup users - wrong JSON`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    configureAuthServer()
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
    }*/
}
