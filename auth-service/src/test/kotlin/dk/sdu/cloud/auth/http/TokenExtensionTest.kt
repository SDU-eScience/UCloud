package dk.sdu.cloud.auth.http

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.OneTimeTokenHibernateDAO
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.RefreshTokenHibernateDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UniqueUsernameService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.test.KtorApplicationTestContext
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.mockk
import kotlin.test.Test

class TokenExtensionTest {
    private lateinit var passwordHashingService: PasswordHashingService
    private lateinit var userDao: UserHibernateDAO
    private lateinit var userCreationService: UserCreationService<HibernateSession>
    private lateinit var usernameGenerator: UniqueUsernameService<HibernateSession>
    private lateinit var personService: PersonService
    private lateinit var ottDao: OneTimeTokenHibernateDAO
    private lateinit var refreshTokenDao: RefreshTokenHibernateDAO
    private lateinit var tokenValidationJWT: TokenValidationJWT
    private lateinit var jwtFactory: JWTFactory
    private lateinit var tokenService: TokenService<HibernateSession>
    private lateinit var db: HibernateSessionFactory

    private fun KtorApplicationTestSetupContext.setup(
        extensionScopes: Map<String, Set<SecurityScope>>
    ): List<Controller> {
        micro.install(HibernateFeature)
        db = micro.hibernateDatabase

        tokenValidationJWT = micro.tokenValidation as TokenValidationJWT
        jwtFactory = JWTFactory(tokenValidationJWT.algorithm)

        passwordHashingService = PasswordHashingService()
        userDao = UserHibernateDAO(passwordHashingService)
        ottDao = OneTimeTokenHibernateDAO()
        refreshTokenDao = RefreshTokenHibernateDAO()

        userCreationService = UserCreationService(db, userDao, mockk(relaxed = true))
        usernameGenerator = UniqueUsernameService(db, userDao)
        personService = PersonService(passwordHashingService, usernameGenerator)
        tokenService = TokenService(
            db,
            personService,
            userDao,
            refreshTokenDao,
            jwtFactory,
            userCreationService,
            tokenValidationJWT,
            extensionScopes
        )

        return listOf(CoreAuthController(db, ottDao, tokenService, tokenValidationJWT))
    }

    private fun createUser(securityPrincipal: SecurityPrincipal): Person.ByPassword {
        val principal = personService.createUserByPassword(
            email = securityPrincipal.username,
            firstNames = securityPrincipal.firstName,
            lastName = securityPrincipal.lastName,
            role = securityPrincipal.role,
            password = "asdqwe"
        )

        userCreationService.blockingCreateUser(principal)
        return principal
    }

    private fun KtorApplicationTestContext.extendToken(
        currentToken: String,
        scope: SecurityScope,
        extender: SecurityPrincipal,
        allowRefreshes: Boolean
    ): TokenExtensionResponse {
        return sendJson(
            HttpMethod.Post,
            "/auth/extend",
            TokenExtensionRequest(
                validJWT = currentToken,
                requestedScopes = listOf(
                    scope.toString()
                ),
                expiresIn = 1000L * 60,
                allowRefreshes = allowRefreshes
            ),
            extender
        ).parseSuccessful()
    }

    @Test
    fun `test extension chaining (no refreshing)`() {
        val securityPrincipal = TestUsers.user
        val realExtensionChain = listOf(TestUsers.service, TestUsers.service2, TestUsers.service3)
        val scope = SecurityScope.construct(listOf("test"), AccessRight.READ_WRITE)

        withKtorTest(
            setup = {
                setup(
                    realExtensionChain.map { it.username to setOf(scope) }.toMap()
                )
            },

            test = {
                val principal = createUser(securityPrincipal)

                val initialToken = tokenService.createAndRegisterTokenFor(principal).accessToken
                val initialPrincipalToken =
                    tokenValidationJWT.decodeToken(tokenValidationJWT.validate(initialToken))
                assertThatPropertyEquals(initialPrincipalToken, { it.extendedByChain.size }, 0)
                assertThatPropertyEquals(initialPrincipalToken, { it.extendedBy }, null)

                var currentToken = initialToken
                realExtensionChain.forEachIndexed { i, extender ->
                    val response = extendToken(currentToken, scope, extender, allowRefreshes = false)
                    currentToken = response.accessToken

                    val currentPrincipalToken =
                        tokenValidationJWT.decodeToken(tokenValidationJWT.validate(currentToken))

                    assertThatPropertyEquals(
                        currentPrincipalToken,
                        { it.extendedByChain },
                        realExtensionChain.subList(0, i + 1).map { it.username }
                    )
                    assertThatPropertyEquals(currentPrincipalToken, { it.extendedBy }, extender.username)
                }
            }
        )
    }

    @Test
    fun `test extension chaining (with refreshing)`() {
        val securityPrincipal = TestUsers.user
        val realExtensionChain = listOf(TestUsers.service, TestUsers.service2, TestUsers.service3)
        val scope = SecurityScope.construct(listOf("test"), AccessRight.READ_WRITE)

        withKtorTest(
            setup = {
                setup(
                    realExtensionChain.map { it.username to setOf(scope) }.toMap()
                )
            },

            test = {
                val principal = createUser(securityPrincipal)
                val initialToken = tokenService.createAndRegisterTokenFor(principal).accessToken
                val initialPrincipalToken =
                    tokenValidationJWT.decodeToken(tokenValidationJWT.validate(initialToken))
                assertThatPropertyEquals(initialPrincipalToken, { it.extendedByChain.size }, 0)
                assertThatPropertyEquals(initialPrincipalToken, { it.extendedBy }, null)


                var currentToken = initialToken
                realExtensionChain.forEachIndexed { i, extender ->


                    val response = extendToken(currentToken, scope, extender, allowRefreshes = true)
                    currentToken = response.accessToken
                    val currentPrincipalToken =
                        tokenValidationJWT.decodeToken(tokenValidationJWT.validate(currentToken))
                    validateToken(realExtensionChain, currentPrincipalToken, extender, i)

                    // Refresh the extended token. It should still contain the same chain.
                    val refreshedToken = sendRequest(
                        HttpMethod.Post,
                        "/auth/refresh",
                        null,
                        configure = {
                            addHeader("Authorization", "Bearer ${response.refreshToken}")
                        }
                    ).parseSuccessful<AccessToken>().accessToken

                    val refreshedPrincipalToken =
                        tokenValidationJWT.decodeToken(tokenValidationJWT.validate(refreshedToken))

                    validateToken(realExtensionChain, refreshedPrincipalToken, extender, i)
                }
            }
        )
    }

    private fun validateToken(
        realExtensionChain: List<SecurityPrincipal>,
        currentPrincipalToken: SecurityPrincipalToken,
        extender: SecurityPrincipal,
        i: Int
    ) {
        assertThatPropertyEquals(
            currentPrincipalToken,
            { it.extendedByChain },
            realExtensionChain.subList(0, i + 1).map { it.username }
        )
        assertThatPropertyEquals(currentPrincipalToken, { it.extendedBy }, extender.username)
    }
}
