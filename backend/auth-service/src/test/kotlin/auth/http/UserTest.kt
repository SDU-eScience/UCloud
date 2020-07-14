package dk.sdu.cloud.auth.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.AuthenticationTokens
import dk.sdu.cloud.auth.api.ChangePasswordRequest
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorAsyncDAO
import dk.sdu.cloud.auth.services.UserAsyncDAO
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.test.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(AuthServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun before() {
        dbTruncate(db)
    }

    @AfterTest
    fun after() {
        dbTruncate(db)
    }

    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    private val password = "ThisIsPassword"
    private val adminUser = TestUsers.admin
    private val email = "email@email"


    private fun KtorApplicationTestSetupContext.configureAuthServer(
        userDao: UserAsyncDAO,
        asfactory: AsyncDBSessionFactory = mockk(relaxed = true),
        userCreationService: UserCreationService = mockk(relaxed = true)
    ): List<UserController> {
        val tokenService = mockk<TokenService>(relaxed = true)
        coEvery {
            tokenService.createAndRegisterTokenFor(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns AuthenticationTokens("tok", "tok", "tok")
        return listOf(
            UserController(
                asfactory,
                personService,
                userDao,
                userCreationService,
                mockk(relaxed = true),
                tokenService,
                listOf("password-reset-service")
            )
        )
    }

    @Test
    fun `create user and lookup`() {
        withKtorTest(
            setup = {
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
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
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, email, adminUser.role))
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, email, adminUser.role))
                        )

                    request.assertSuccess()
                }

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, email, adminUser.role))
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },

            test = {
                val user = TestUsers.user
                val request =
                    sendJson(
                        method = HttpMethod.Post,
                        path = "/auth/users/register",
                        user = user,
                        request = listOf(CreateSingleUserRequest(user.username, password, email, user.role))
                    )

                request.assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }

    @Test
    fun `create user - wrong JSON`() {
        withKtorTest(
            setup = {
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, email, adminUser.role))
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/register",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest(adminUser.username, password, email,  adminUser.role))
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/password",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest("wrong", "request", email, adminUser.role))
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
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
                val userDao = UserAsyncDAO(passwordHashingService, TwoFactorAsyncDAO())
                val userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
                configureAuthServer(userDao, db, userCreationService)
            },
            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/auth/users/lookup",
                            user = adminUser,
                            request = listOf(CreateSingleUserRequest("wrong", "request", email, adminUser.role))
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
                }
            }
        )
    }
}
