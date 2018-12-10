package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateUserRequest
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.createTokenForService
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.mockk
import org.hibernate.Session
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun createTokenForUser(username: String = "user", role: Role = Role.USER): String {
    return when (role) {
        Role.USER, Role.ADMIN, Role.GUEST -> TokenValidationMock.createTokenForUser(username, role)
        Role.SERVICE -> TokenValidationMock.createTokenForService(username)
        else -> TODO()
    }
}

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer ${createTokenForUser(username, role)}")
}


class UserTest {
    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    private fun KtorApplicationTestSetupContext.configureAuthServer(
        userDao: UserHibernateDAO,
        hsfactory: HibernateSessionFactory = mockk(relaxed = true),
        userCreationService: UserCreationService<Session> = mockk(relaxed = true)
    ): List<UserController<HibernateSession>> {
        return listOf(
            UserController(
                hsfactory,
                personService,
                userDao,
                userCreationService,
                mockk(relaxed = true)
            )
        )
    }

    @Test
    fun `create user and lookup`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `create user - not admin`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `create user - wrong JSON`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `change password`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `change password - wrong original password`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `change password - wrong JSON`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `lookup users - not admin`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `lookup users - wrong JSON`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `get all users test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                with(engine) {
                    for (i in 1..20) {
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = TestUsers.admin,
                            request = CreateUserRequest("user$i", "pass", Role.USER)
                        ).assertSuccess()

                    }

                    val request = sendRequest(
                        method = HttpMethod.Get,
                        path = "/auth/users/all",
                        user = TestUsers.service.copy(username = "_accounting")
                    )

                    request.assertSuccess()
                    println(request.response.content)
                }
            }
        )
    }
}
