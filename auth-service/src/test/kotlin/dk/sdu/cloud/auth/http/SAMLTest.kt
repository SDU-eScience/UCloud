package dk.sdu.cloud.auth.http

import com.onelogin.saml2.model.Organization
import com.onelogin.saml2.settings.Saml2Settings
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.auth.utils.withAuthMock
import dk.sdu.cloud.auth.utils.withDatabase
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.junit.Test
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SAMLTest {

    private data class TestContext(
        val userDao: UserHibernateDAO,
        val refreshTokenDao: RefreshTokenHibernateDAO,
        val jwtAlg: JWTAlgorithm,
        val tokenService: TokenService<HibernateSession>,
        val authSettings: Saml2Settings,
        val samlRequestProcessorFactory: SAMLRequestProcessorFactory
    )

    private fun Application.createSamlController(db: HibernateSessionFactory): TestContext {
        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val jwtAlg = JWTAlgorithm.HMAC256("foobar")

        val tokenService = TokenService(
            db,
            userDao,
            refreshTokenDao,
            jwtAlg,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        installDefaultFeatures(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            requireJobId = true
        )

        install(JWTProtection)

        val authSettings = mockk<Saml2Settings>()
        val samlRequestProcessorFactory = mockk<SAMLRequestProcessorFactory>()

        routing {
            SAMLController(
                authSettings,
                samlRequestProcessorFactory,
                tokenService
            ).configure(this)
        }

        return TestContext(userDao, refreshTokenDao, jwtAlg, tokenService, authSettings, samlRequestProcessorFactory)
    }

    private val samlResponse = Util.base64encoder("""
                            <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                            ID="_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6"
                            Version="2.0" IssueInstant="2014-07-17T01:01:48Z"
                            Destination="http://sp.example.com/demo1/index.php?acs"
                            InResponseTo="ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685">
                            </samlp:Response>
                            """.trimIndent())

    @Test
    fun `Metadata Test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        with(createSamlController(db)) {
                            every { authSettings.spMetadata } returns "All this is metadata"
                        }
                    },

                    test = {

                        val response =
                            handleRequest(HttpMethod.Get, "/auth/saml/metadata") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        assertEquals("All this is metadata", response.content)
                    }
                )
            }
        }
    }

    @Test
    fun `Login Test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        with(createSamlController(db)) {
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
                        }
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `Login Test - no service given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createSamlController(db)
                    },

                    test = {

                        val response =
                            handleRequest(HttpMethod.Get, "/auth/saml/login") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.Found, response.status())
                        val result = response.headers.values("Location").toString().trim('[', ']')
                        assertEquals("/auth/login", result)
                    }
                )
            }
        }
    }

    @Test
    fun `acs Test - invalid relaystate`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createSamlController(db)
                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `acs Test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        with(createSamlController(db)) {
                            every { samlRequestProcessorFactory.invoke(any(), any(), any()) } answers {
                                val response = mockk<SamlRequestProcessor>()
                                coEvery { response.processResponse(any()) } just Runs
                                every { response.authenticated } returns true
                                every { response.attributes } answers {
                                    val h = HashMap<String, List<String>>(10)
                                    h.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.10", listOf("test"))
                                    h.put("gn", listOf("gn"))
                                    h.put("sn", listOf("sn"))
                                    h.put("schacHomeOrganization", listOf("SDU"))
                                    h
                                }
                                response
                            }

                        }

                    },

                    test = {

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

                        assertEquals(HttpStatusCode.Found, response.status())
                        assertTrue(response.headers.values("Location").toString().contains(
                            "/auth/login-redirect?service=_service")
                        )
                        assertTrue(response.headers.values("Location").toString().contains("accessToken"))
                        assertTrue(response.headers.values("Location").toString().contains("refreshToken"))

                    }
                )
            }
        }
    }

    @Test
    fun `acs Test - user Not authenticated`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        with(createSamlController(db)) {
                            every { samlRequestProcessorFactory.invoke(any(), any(), any()) } answers {
                                val response = mockk<SamlRequestProcessor>()
                                coEvery { response.processResponse(any()) } just Runs
                                response
                            }
                        }

                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `acs Test - invalid response, User not found`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        with(createSamlController(db)) {
                            every { samlRequestProcessorFactory.invoke(any(),any(), any())} answers {
                                val response = mockk<SamlRequestProcessor>()
                                coEvery { response.processResponse(any()) } just Runs
                                response
                            }
                        }

                    },

                    test = {

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
                )
            }
        }
    }

    @Test
    fun `acs Test - no params given`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        createSamlController(db)
                    },

                    test = {

                        val response =
                            handleRequest(HttpMethod.Post, "/auth/saml/acs") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                                setBody("null")
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                )
            }
        }
    }
}