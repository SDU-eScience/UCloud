package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.RefreshTokenHibernateDAO
import dk.sdu.cloud.auth.services.Service
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class PasswordTest {
    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

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

        val userDao = UserHibernateDAO(passwordHashingService)
        val refreshTokenDao = RefreshTokenHibernateDAO()

        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val jwtFactory = JWTFactory(tokenValidation.algorithm)

        val tokenService = TokenService(
            micro.hibernateDatabase,
            personService,
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
                passwordHashingService,
                userDao,
                loginResponder
            )
        )

        return TestContext(userDao, refreshTokenDao, jwtFactory, tokenService, micro.hibernateDatabase, controllers)
    }

    private val person = personService.createUserByPassword(
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
                        UserHibernateDAO(passwordHashingService).insert(it, person)
                        ServiceDAO.insert(Service(name = "_service", endpoint = "http://service"))
                    }

                    controllers
                }
            },

            test = {
                val sendRequest = sendRequest(HttpMethod.Post, "/auth/login", user = null) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    addHeader(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    setBody("username=user1&password=pass1234&service=_service")
                }
                sendRequest.assertSuccess()
            }
        )
    }

    @Test
    fun `Login test - password wrong`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    db.withTransaction {
                        UserHibernateDAO(passwordHashingService).insert(it, person)
                    }

                    controllers
                }
            },
            test = {
                val resp = sendRequest(HttpMethod.Post, "/auth/login", user = null) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody("username=user1&password=wrongpassword&service=_service")
                }

                resp.assertStatus(HttpStatusCode.Unauthorized)
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
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/auth/login?",
                            user = null,
                            configure = {
                                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                                setBody("username=user1&password=pass1234&service=_service")
                            }
                        )
                    request.assertStatus(HttpStatusCode.Unauthorized)
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
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/auth/login?",
                            user = null,
                            configure = {
                                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                                setBody("username=user1&password=pass1234")
                            }
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
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
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/auth/login?",
                            user = null,
                            configure = {
                                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                                setBody("username=user1&service=_service")
                            }
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
                }

                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/auth/login?",
                            user = null,
                            configure = {
                                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                                setBody("password=pass1234&service=_service")
                            }
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
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
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/auth/login",
                            user = null
                        )

                    request.assertStatus(HttpStatusCode.BadRequest)
                }
            }
        )
    }
}
