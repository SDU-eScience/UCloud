package dk.sdu.cloud.auth.http

import com.onelogin.saml2.model.Organization
import com.onelogin.saml2.settings.Saml2Settings
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.RefreshTokenHibernateDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.service.tokenValidation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Test
import java.net.URL
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SAMLTest {
    private data class TestContext(
        val userDao: UserHibernateDAO,
        val refreshTokenDao: RefreshTokenHibernateDAO,
        val jwtFactory: JWTFactory,
        val tokenService: TokenService<HibernateSession>,
        val authSettings: Saml2Settings,
        val samlRequestProcessorFactory: SAMLRequestProcessorFactory,
        val controllers: List<Controller>
    )

    private fun KtorApplicationTestSetupContext.createSamlController(): TestContext {
        micro.install(HibernateFeature)

        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()

        val validation = micro.tokenValidation as TokenValidationJWT
        val jwtFactory = JWTFactory(validation.algorithm)

        val tokenService = TokenService(
            micro.hibernateDatabase,
            userDao,
            refreshTokenDao,
            jwtFactory,
            UserCreationService(micro.hibernateDatabase, userDao, mockk(relaxed = true)),
            tokenValidation = validation
        )

        val authSettings = mockk<Saml2Settings>()
        val samlRequestProcessorFactory = mockk<SAMLRequestProcessorFactory>()

        val twoFactorChallengeService = mockk<TwoFactorChallengeService<HibernateSession>>(relaxed = true)
        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)
        every { twoFactorChallengeService.isConnected(any()) } returns false
        every { twoFactorChallengeService.createLoginChallengeOrNull(any(), any()) } returns null

        val controllers = listOf(
            SAMLController(
                authSettings,
                samlRequestProcessorFactory,
                tokenService,
                loginResponder
            )
        )

        return TestContext(
            userDao,
            refreshTokenDao,
            jwtFactory,
            tokenService,
            authSettings,
            samlRequestProcessorFactory,
            controllers
        )
    }

    private val samlResponse = Util.base64encoder(
        """
        <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
        xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
        ID="_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6"
        Version="2.0" IssueInstant="2014-07-17T01:01:48Z"
        Destination="http://sp.example.com/demo1/index.php?acs"
        InResponseTo="ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685">
        </samlp:Response>
        """.trimIndent()
    )

    @Test
    fun `Metadata Test`() {
        val metadataString = "All this is metadata"
        withKtorTest(
            setup = {
                with(createSamlController()) {
                    every { authSettings.spMetadata } returns metadataString
                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/auth/saml/metadata") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    assertEquals(metadataString, response.content)
                }
            }
        )
    }

    @Test
    fun `Login Test`() {
        withKtorTest(
            setup = {
                with(createSamlController()) {
                    every { authSettings.idpSingleSignOnServiceUrl } returns URL("https://ThisURLForSSO.com")
                    every { authSettings.spNameIDFormat } returns "NameIDFormat"
                    every { authSettings.wantNameIdEncrypted } returns false
                    every { authSettings.organization } returns
                            Organization(
                                "SDU",
                                "SDUDisplay",
                                URL("https://sdu.dk")
                            )
                    every { authSettings.spAssertionConsumerServiceUrl } returns
                            URL("https://ThisURLForConsumer.com")
                    every { authSettings.spEntityId } returns "Entity ID"
                    every { authSettings.requestedAuthnContext } returns listOf("requestedAuthn")
                    every { authSettings.requestedAuthnContextComparison } returns "compare"
                    every { authSettings.isCompressRequestEnabled } returns true
                    every { authSettings.authnRequestsSigned } returns false

                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/auth/saml/login?service=_service") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    assertTrue(response.headers.values("Location").toString().contains("https://ThisURLForSSO.com"))
                    assertTrue(response.headers.values("Location").toString().contains("SAMLRequest"))
                    assertTrue(response.headers.values("Location").toString().contains("RelayState"))
                }
            }
        )
    }

    @Test
    fun `Login Test - no service given`() {
        withKtorTest(
            setup = {
                createSamlController().controllers
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/auth/saml/login") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login", result)
                }
            }
        )
    }

    @Test
    fun `acs Test - invalid relaystate`() {
        withKtorTest(
            setup = {
                createSamlController().controllers
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody("RelayState=Flimflam")
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    val result = response.headers.values("Location").toString().trim('[', ']')
                    assertEquals("/auth/login", result)
                }
            }
        )
    }

    @Test
    fun `acs Test`() {
        withKtorTest(
            setup = {
                with(createSamlController()) {
                    every { samlRequestProcessorFactory.invoke(any(), any(), any()) } answers {
                        val response = mockk<SamlRequestProcessor>()
                        coEvery { response.processResponse(any()) } just Runs
                        every { response.authenticated } returns true
                        every { response.attributes } answers {
                            hashMapOf(
                                AttributeURIs.EduPersonTargetedId to listOf("test"),
                                "gn" to listOf("gn"),
                                "sn" to listOf("sn"),
                                "schacHomeOrganization" to listOf("SDU")
                            )
                        }
                        response
                    }

                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setBody(
                                "RelayState=http://thisIsAWebSite.com/auth/saml/login?service=_service" +
                                        "&" +
                                        "SAMLResponse=$samlResponse"
                            )
                        }.response

                    assertEquals(HttpStatusCode.Found, response.status())
                    assertTrue(
                        response.headers.values("Location").toString().contains(
                            "/auth/login-redirect?service=_service"
                        )
                    )
                    assertTrue(response.headers.values("Location").toString().contains("accessToken"))
                    assertTrue(response.headers.values("Location").toString().contains("refreshToken"))
                }
            }
        )
    }

    @Test
    fun `acs Test - user Not authenticated`() {
        withKtorTest(
            setup = {
                with(createSamlController()) {
                    every { samlRequestProcessorFactory.invoke(any(), any(), any()) } answers {
                        val response = mockk<SamlRequestProcessor>()
                        coEvery { response.processResponse(any()) } just Runs
                        response
                    }

                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody(
                                "RelayState=http://thisIsAWebSite.com/auth/saml/login?service=_service" +
                                        "&" +
                                        "SAMLResponse=$samlResponse"
                            )
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())

                }
            }
        )
    }

    @Test
    fun `acs Test - invalid response, User not found`() {
        withKtorTest(
            setup = {
                with(createSamlController()) {
                    every { samlRequestProcessorFactory.invoke(any(), any(), any()) } answers {
                        val response = mockk<SamlRequestProcessor>()
                        coEvery { response.processResponse(any()) } just Runs
                        response
                    }

                    controllers
                }
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            setUser(role = Role.ADMIN)
                            setBody(
                                "RelayState=http://thisIsAWebSite.com/auth/saml/login?service=_service" +
                                        "&" +
                                        "SAMLResponse=$samlResponse"
                            )
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            }
        )
    }

    @Test
    fun `acs Test - no params given`() {
        withKtorTest(
            setup = {
                createSamlController().controllers
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                            setBody("null")
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }
}
