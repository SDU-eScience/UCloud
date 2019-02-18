package dk.sdu.cloud.auth.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.ChangePasswordRequest
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.createTokenForService
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.mockk.mockk
import org.hibernate.Session
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private val password = "ThisIsPassword"
    private val adminUser = TestUsers.admin


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
                mockk(relaxed = true),
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
                val lookup1 = adminUser.username
                val lookup2 = "User2"
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/lookup",
                            user = adminUser,
                            request = LookupUsersRequest(listOf(lookup1, lookup2))
                        )

                    request.assertSuccess()
                    val response = defaultMapper.readValue<LookupUsersResponse>(request.response.content!!)
                    assertEquals(2, response.results.size)
                    assertNull(response.results[lookup1])
                    assertNull(response.results[lookup2])

                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, adminUser.role))
                        )

                    request.assertSuccess()
                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/lookup",
                            user = adminUser,
                            request = LookupUsersRequest(listOf(lookup1, lookup2))
                        )

                    request.assertSuccess()

                    val response = defaultMapper.readValue<LookupUsersResponse>(request.response.content!!)
                    assertEquals(2, response.results.size)
                    assertEquals(lookup1, response.results[lookup1]?.subject)
                    assertEquals(adminUser.role, response.results[lookup1]?.role)
                    assertNull(response.results[lookup2])
                }

            }
        )
    }

    @Test
    fun `create user - duplicate`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val userDao = UserHibernateDAO(passwordHashingService)
                val userCreationService = UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, micro.hibernateDatabase, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, adminUser.role))
                        )

                    request.assertSuccess()
                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, adminUser.role))
                        )

                    request.assertStatus(HttpStatusCode.Conflict)
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
                val user = TestUsers.user
                val request =
                    sendJson(
                        method = HttpMethod.Post,
                        path = "/auth/users/register",
                        user = user,
                        request = listOf(CreateSingleUserRequest(user.username, password, user.role))
                    )

                request.assertStatus(HttpStatusCode.Unauthorized)
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = ChangePasswordRequest("wrong", "request")
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, adminUser.role))
                        )

                    request.assertSuccess()
                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/password",
                            user = adminUser,
                            request = ChangePasswordRequest(
                                currentPassword = password,
                                newPassword = "Pass"
                            )
                        )
                    request.assertSuccess()
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, adminUser.role))
                        )

                    request.assertSuccess()
                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/password",
                            user = adminUser,
                            request = ChangePasswordRequest(
                                currentPassword = "Wrong password",
                                newPassword = "Pass"
                            )
                        )
                    request.assertStatus(HttpStatusCode.BadRequest)
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/password",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest("wrong", "request", adminUser.role))
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "auth/users/lookup",
                            user = TestUsers.user,
                            request = LookupUsersRequest(listOf("user1", "user2", "user3"))
                        )
                    request.assertStatus(HttpStatusCode.Unauthorized)
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
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/lookup",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest("wrong", "request", adminUser.role))
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
                }
            }
        )
    }
}
