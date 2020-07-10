package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.testUtil.dbTruncate
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class PasswordTest {
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

    private data class TestContext(
        val userDao: UserAsyncDAO,
        val refreshTokenDao: RefreshTokenAsyncDAO,
        val jwtFactory: JWTFactory,
        val tokenService: TokenService,
        val db: AsyncDBSessionFactory,
        val controllers: List<Controller>
    )

    private fun KtorApplicationTestSetupContext.createPasswordController(): TestContext {
        val twoFactorDao = TwoFactorAsyncDAO()
        val userDao = UserAsyncDAO(passwordHashingService, twoFactorDao)
        val refreshTokenDao = RefreshTokenAsyncDAO()

        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val jwtFactory = JWTFactory(tokenValidation.algorithm)

        val tokenService = TokenService(
            db,
            personService,
            userDao,
            refreshTokenDao,
            jwtFactory,
            mockk(relaxed = true),
            tokenValidation = tokenValidation
        )

        val twoFactorChallengeService = mockk<TwoFactorChallengeService>(relaxed = true)
        coEvery { twoFactorChallengeService.isConnected(any()) } returns false
        coEvery { twoFactorChallengeService.createLoginChallengeOrNull(any(), any()) } returns null

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)
        val loginService = LoginService(
            db,
            passwordHashingService,
            userDao,
            LoginAttemptAsyncDao(),
            loginResponder
        )

        val controllers = listOf(
            PasswordController(loginService)
        )

        return TestContext(userDao, refreshTokenDao, jwtFactory, tokenService, db, controllers)
    }

    private val person = personService.createUserByPassword(
        "Firstname",
        "lastname",
        "user1",
        Role.ADMIN,
        "pass1234",
        "email@email"
    )

    @Test
    fun `Login test`() {
        withKtorTest(
            setup = {
                with(createPasswordController()) {
                    runBlocking {
                        db.withTransaction {
                            userDao.insert(it, person)
                            ServiceDAO.insert(Service(name = "_service", endpoint = "http://service"))
                        }

                        controllers
                    }
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
                runBlocking {
                    with(createPasswordController()) {
                        db.withTransaction {
                            userDao.insert(it, person)
                        }

                        controllers
                    }
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
