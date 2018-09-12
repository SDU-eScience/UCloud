package dk.sdu.cloud.auth.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.AuthConfiguration
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.utils.createServiceJWTWithTestAlgorithm
import dk.sdu.cloud.auth.utils.testJwtFactory
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.toSecurityToken
import io.ktor.application.Application
import io.ktor.http.*
import io.ktor.routing.routing
import io.ktor.server.testing.*
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreAuthTest {
    private data class TestContext(
        val db: HibernateSessionFactory,
        val ottDao: OneTimeTokenHibernateDAO,
        val userdao: UserHibernateDAO,
        val refreshTokenDao: RefreshTokenHibernateDAO,
        val jwtFactory: JWTFactory,
        val config: AuthConfiguration,
        val tokenService: TokenService<HibernateSession>
    )

    private fun Application.createCoreAuthController(
        db: HibernateSessionFactory,
        enablePassword: Boolean,
        enableWayf: Boolean,
        serviceExtensionPolicy: Map<String, Set<SecurityScope>> = emptyMap()
    ): TestContext {
        val ottDao = OneTimeTokenHibernateDAO()
        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val config = mockk<AuthConfiguration>()
        val tokenService = TokenService(
            db,
            userDao,
            refreshTokenDao,
            testJwtFactory,
            mockk(relaxed = true),
            allowedServiceExtensionScopes = serviceExtensionPolicy
        )
        installDefaultFeatures(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            requireJobId = true
        )

        routing {
            CoreAuthController(
                db,
                ottDao,
                tokenService,
                enablePassword,
                enableWayf
            ).configure(this)
        }

        return TestContext(db, ottDao, userDao, refreshTokenDao, testJwtFactory, config, tokenService)
    }

    private fun withBasicSetup(
        enablePassword: Boolean = true,
        enableWayf: Boolean = false,
        serviceExtensionPolicy: Map<String, Set<SecurityScope>> = emptyMap(),
        test: TestApplicationEngine.(ctx: TestContext) -> Unit
    ) {
        lateinit var ctx: TestContext
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        ctx = createCoreAuthController(db, enablePassword, enableWayf, serviceExtensionPolicy)
                    },

                    test = {
                        test(ctx)
                    }
                )
            }
        }
    }

    private fun TestContext.createUser(
        session: HibernateSession,
        username: String,
        role: Role = Role.USER,
        password: String = "password"
    ) {
        userdao.insert(
            session,
            PersonUtils.createUserByPassword(
                "firstname",
                "lastname",
                username,
                role,
                password
            )
        )
    }

    private fun TestContext.createRefreshToken(
        session: HibernateSession,
        username: String,
        role: Role = Role.USER,
        csrf: String = "csrf"
    ): RefreshTokenAndUser {
        val tokenAndUser = RefreshTokenAndUser(username, "$username/$role", csrf)
        refreshTokenDao.insert(session, tokenAndUser)
        return tokenAndUser
    }

    private val person = PersonUtils.createUserByPassword(
        "firstname",
        "lastname",
        "user",
        Role.ADMIN,
        "password"
    )

    fun TestApplicationEngine.sendRequest(
        method: HttpMethod,
        uri: String,
        user: String = "user",
        role: Role = Role.USER,
        addUser: Boolean = true,
        setup: TestApplicationRequest.() -> Unit = {}
    ): TestApplicationCall {
        return handleRequest(method, uri) {
            addHeader("Job-Id", UUID.randomUUID().toString())
            if (addUser) setUser(username = user, role = role)
            setup()
        }
    }

    @Test
    fun `Login Test - service given, isInvalid True, Wayf false, password true`() {
        withBasicSetup(enablePassword = true, enableWayf = false) { _ ->
            val serviceName = "_service"
            ServiceDAO.insert(Service(serviceName, "endpointOfService"))
            val response =
                sendRequest(HttpMethod.Get, "/auth/login?service=$serviceName&invalid=true", addUser = false).response
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Login Test - service given, isInvalid True, Wayf true, password false`() {
        withBasicSetup(enablePassword = false, enableWayf = true) { ctx ->
            val serviceName = "_service"
            ServiceDAO.insert(Service(serviceName, "endpointOfService"))
            val response =
                sendRequest(HttpMethod.Get, "/auth/login?service=$serviceName&isInvalid=true", addUser = false).response
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Login test - no service given`() {
        withBasicSetup {
            val response = sendRequest(HttpMethod.Get, "/auth/login", addUser = false).response
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Redirect login test - no service given`() {
        withBasicSetup {
            val response = sendRequest(HttpMethod.Get, "/auth/login-redirect").response
            assertEquals(HttpStatusCode.Found, response.status())
            val result = response.headers.values("Location").toString().trim('[', ']')
            assertEquals("/auth/login", result)
        }
    }

    @Test
    fun `Redirect login test - service given, no accessToken given`() {
        withBasicSetup {
            val serviceName = "_service"
            ServiceDAO.insert(Service(serviceName, "endpointOfService"))
            val response = sendRequest(HttpMethod.Get, "/auth/login-redirect?service=$serviceName").response
            assertEquals(HttpStatusCode.Found, response.status())
            val result = response.headers.values("Location").toString().trim('[', ']')
            assertEquals("/auth/login?invalid&service=$serviceName", result)
        }
    }

    @Test
    fun `Redirect login test - service given, accessToken given`() {
        withBasicSetup {
            val serviceName = "_service"
            ServiceDAO.insert(Service(serviceName, "endpointOfService"))
            val jwt = createServiceJWTWithTestAlgorithm(serviceName).token

            val response = sendRequest(
                HttpMethod.Get,
                "/auth/login-redirect?service=$serviceName&accessToken=$jwt&refreshToken=rtoken"
            ).response

            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Refresh test`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            lateinit var refreshToken: RefreshTokenAndUser
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                refreshToken = ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/refresh", addUser = false) {
                addHeader(HttpHeaders.Authorization, "Bearer ${refreshToken.token}")
            }.response
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Refresh test - unauthorized`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/refresh", addUser = false) {
                addHeader("Authorization", "Bearer bad-token")
            }.response
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Refresh test - no token`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/refresh", addUser = false).response
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Request test`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(
                HttpMethod.Post,
                "/auth/request?audience=${SecurityScope.ALL_WRITE}",
                user = username,
                role = role
            ).response
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `Request test - missing params`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/request?user", user = username, role = role).response
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Claim test`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            val jti = "givenJTI"
            ctx.db.withTransaction {
                ctx.createUser(it, username, role)
                ctx.createRefreshToken(it, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/claim/$jti", user = username, role = role).response
            assertEquals(HttpStatusCode.NoContent, response.status())
        }
    }

    @Test
    fun `Claim test - Unauthorized not ADMIN or SERVICE`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.USER
            val jti = "givenJTI"
            ctx.db.withTransaction { session ->
                ctx.createUser(session, username, role)
                ctx.createRefreshToken(session, username, role)
            }

            val response =
                sendRequest(HttpMethod.Post, "/auth/claim/$jti", user = username, role = role).response
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Claim test - No token`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.USER
            ctx.db.withTransaction { session ->
                ctx.createUser(session, username, role)
                ctx.createRefreshToken(session, username, role)
            }

            val response =
                sendRequest(HttpMethod.Post, "/auth/claim/givenJTI", addUser = false).response
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Claim test - claim same`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.ADMIN
            ctx.db.withTransaction { session ->
                ctx.createUser(session, username, role)
                ctx.createRefreshToken(session, username, role)
            }

            val response1 = sendRequest(HttpMethod.Post, "/auth/claim/givenJTI", user = username, role = role).response
            assertEquals(HttpStatusCode.NoContent, response1.status())

            val response2 = sendRequest(HttpMethod.Post, "/auth/claim/givenJTI", user = username, role = role).response
            assertEquals(HttpStatusCode.Conflict, response2.status())
        }
    }

    @Test
    fun `Logout Test`() {
        withBasicSetup { ctx ->
            val (username, role) = "user" to Role.USER
            lateinit var refreshToken: RefreshTokenAndUser
            ctx.db.withTransaction { session ->
                ctx.createUser(session, username, role)
                refreshToken = ctx.createRefreshToken(session, username, role)
            }

            val response = sendRequest(HttpMethod.Post, "/auth/logout", addUser = false) {
                addHeader(HttpHeaders.Authorization, "Bearer ${refreshToken.token}")
            }.response
            assertEquals(HttpStatusCode.NoContent, response.status())
        }
    }

    private fun TestApplicationEngine.webRefreshInitialize(ctx: TestContext): RefreshTokenAndUser {
        val (username, role) = "user" to Role.USER
        lateinit var refreshToken: RefreshTokenAndUser
        ctx.db.withTransaction { session ->
            ctx.createUser(session, username, role)
            refreshToken = ctx.createRefreshToken(session, username, role)
        }

        return refreshToken
    }


    private fun TestApplicationEngine.webRefresh(
        refreshToken: RefreshTokenAndUser,
        addCsrfToken: Boolean = true,
        addRefreshToken: Boolean = true,
        vararg headersToUse: String,
        setup: TestApplicationRequest.() -> Unit = {}
    ): TestApplicationResponse {
        return sendRequest(HttpMethod.Post, "/auth/refresh/web") {
            headersToUse.forEach { addHeader(it, "https://cloud.sdu.dk") }

            if (addCsrfToken) {
                addHeader(CoreAuthController.REFRESH_WEB_CSRF_TOKEN, refreshToken.csrf)
            }

            if (addRefreshToken) {
                addHeader(
                    HttpHeaders.Cookie,
                    renderCookieHeader(
                        Cookie(
                            CoreAuthController.REFRESH_WEB_REFRESH_TOKEN_COOKIE,
                            refreshToken.token
                        )
                    )
                )
            }

            setup()
        }.response
    }

    private fun webRefreshVerifySuccess(response: TestApplicationResponse) {
        assertEquals(HttpStatusCode.OK, response.status())
        val tree = defaultMapper.readTree(response.content!!)

        val accessToken = tree["accessToken"]!!
        val csrfToken = tree["csrfToken"]!!

        assertTrue(accessToken.isTextual)
        assertTrue(csrfToken.isTextual)

        assertTrue(accessToken.asText().isNotBlank())
        assertTrue(csrfToken.asText().isNotBlank())
    }

    @Test
    fun `Web refresh - test happy path (origin)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Origin))
            webRefreshVerifySuccess(response)
        }
    }

    @Test
    fun `Web refresh - test happy path (referrer)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Referrer))
            webRefreshVerifySuccess(response)
        }
    }

    @Test
    fun `Web refresh - test happy path (origin, referrer)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Origin, HttpHeaders.Referrer))
            webRefreshVerifySuccess(response)
        }
    }

    @Test
    fun `Web refresh - test happy path (blank origin, referrer)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Referrer)) {
                addHeader(HttpHeaders.Origin, "         ")
            }
            webRefreshVerifySuccess(response)
        }
    }

    @Test
    fun `Web refresh - test blank referer`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *emptyArray()) {
                addHeader(HttpHeaders.Referrer, "         ")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - malformed (malformed origin)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *emptyArray()) {
                addHeader(HttpHeaders.Origin, "this is not a valid header")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - malformed (origin, malformed referrer)`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *emptyArray()) {
                addHeader(HttpHeaders.Referrer, "this is not a valid header")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }


    @Test
    fun `Web refresh - untrusted origin`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *emptyArray()) {
                addHeader(HttpHeaders.Referrer, "https://evil.com")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - test missing origin and referrer`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *emptyArray())
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - test missing csrf`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, addCsrfToken = false, headersToUse = *arrayOf(HttpHeaders.Origin))
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - test missing refreshToken`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, addRefreshToken = false, headersToUse = *arrayOf(HttpHeaders.Origin))
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `Web refresh - test bad csrf token`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, addCsrfToken = false, headersToUse = *arrayOf(HttpHeaders.Origin)) {
                addHeader(CoreAuthController.REFRESH_WEB_CSRF_TOKEN, "Bad csrf token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Web refresh - test bad refresh token`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, addRefreshToken = false, headersToUse = *arrayOf(HttpHeaders.Origin)) {
                addHeader(
                    HttpHeaders.Cookie,
                    renderCookieHeader(
                        Cookie(
                            CoreAuthController.REFRESH_WEB_REFRESH_TOKEN_COOKIE,
                            "bad refresh token"
                        )
                    )
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `Web refresh - twice with same csrf fails`() {
        withBasicSetup { ctx ->
            val token = webRefreshInitialize(ctx)
            val response = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Origin))
            webRefreshVerifySuccess(response)

            val response2 = webRefresh(token, headersToUse = *arrayOf(HttpHeaders.Origin))
            assertEquals(HttpStatusCode.Unauthorized, response2.status())
        }
    }

    @Test
    fun `Extension test - happy path`() {
        val serviceName = "service"
        val scope = SecurityScope.construct(listOf("foo"), AccessRight.READ_WRITE)
        val extensionPolicy = mapOf(serviceName to setOf(scope))

        val user = "user"
        val userRole = Role.USER
        val userJwt = createTokenForUser(user, userRole)

        val expiresIn = 360000L

        withBasicSetup(serviceExtensionPolicy = extensionPolicy) { ctx ->
            ctx.db.withTransaction { session ->
                ctx.createUser(session, user, userRole)
            }

            val start = System.currentTimeMillis()
            val response = sendRequest(HttpMethod.Post, "/auth/extend", serviceName, Role.SERVICE) {
                setBody(
                    """
                    {
                        "validJWT": "$userJwt",
                        "expiresIn": $expiresIn,
                        "requestedScopes": [
                            "foo:write"
                        ]
                    }
                """.trimIndent()
                )
            }.response

            assertEquals(HttpStatusCode.OK, response.status())
            val parsedResponse = defaultMapper.readValue<TokenExtensionResponse>(response.content!!)

            val parsedToken = TokenValidation.validateOrNull(parsedResponse.accessToken)!!.toSecurityToken()
            assertEquals(user, parsedToken.principal.username)
            assertEquals(userRole, parsedToken.principal.role)
            assertTrue(
                parsedToken.expiresAt - parsedToken.issuedAt >= expiresIn,
                "Expected token to be expire after $expiresIn, but was ${parsedToken.expiresAt - parsedToken.issuedAt}"
            )
        }
    }
}