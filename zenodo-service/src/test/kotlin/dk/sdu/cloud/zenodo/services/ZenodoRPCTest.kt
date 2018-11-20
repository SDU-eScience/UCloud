package dk.sdu.cloud.zenodo.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.zenodo.util.HttpClient
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.asynchttpclient.Response
import org.junit.Test
import java.io.File
import java.net.URL
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

//Internal Server Error tests starts at retry 4 to reduce test time.

class ZenodoRPCTest {
    private val responseBody = """
        {
            "created":"22-2-12",
            "files": [
                "path"
            ],
            "id":"1",
            "links": {
                "discard":"discard",
                "edit":"edit",
                "files":"files",
                "publish":"Publish",
                "newversion":"newversion",
                "self":"self"
            },
            "metadata":{
                "field1":"field1"
            },
            "modified":"modified",
            "owner":229,
            "record_id":2,
            "state":"state",
            "submitted":true,
            "title":"title"
        }
        """.trimIndent()

    private val oauth = mockk<ZenodoOAuth<Unit>>()
    private val zenodo = ZenodoRPCService(true, oauth)
    private val decodedJWT = mockk<DecodedJWT>(relaxed = true).also {
        every { it.subject } returns "user"
    }
    private val oToken = OAuthTokens(
        "access",
        System.currentTimeMillis() + 10000,
        "refresh"
    )

    @Test
    fun `is connected test`() {
        every { oauth.isConnected(any()) } returns true
        assertTrue(zenodo.isConnected(decodedJWT.subject))
    }

    @Test
    fun `is connected - not connected - test`() {
        every { oauth.isConnected(any()) } returns false
        assertFalse(zenodo.isConnected(decodedJWT.subject))
    }

    @Test
    fun `validate Token test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            response
        }
        val result = runBlocking { zenodo.validateUser(decodedJWT.subject) }
        assertEquals(Unit, result)
    }

    @Test(expected = MissingOAuthToken::class)
    fun `validate Token - missing auth - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns null
        runBlocking { zenodo.validateUser(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `validate Token - timeout - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            throw TimeoutException()
        }
        runBlocking { zenodo.validateUser(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `validate Token - Unauthorized -  test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Unauthorized.value
            response
        }
        runBlocking { zenodo.validateUser(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `validate Token - Forbidden -  test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Forbidden.value
            response
        }
        runBlocking { zenodo.validateUser(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `validate Token - Internal Server Error 500 -  test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 500
            response
        }
        runBlocking { zenodo.validateUser(decodedJWT.subject, 4) }
    }

    @Test(expected = TooManyRetries::class)
    fun `validate Token - unknown response status -  test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.get(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 300
            response
        }
        runBlocking { zenodo.validateUser(decodedJWT.subject) }
    }

    @Test
    fun `create Deposition test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            every { response.responseBody } returns responseBody
            response
        }
        val result = runBlocking { zenodo.createDeposition(decodedJWT.subject) }
        assertEquals("22-2-12", result.created)
    }

    @Test(expected = MissingOAuthToken::class)
    fun `create Deposition - Missing auth - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns null

        runBlocking { zenodo.createDeposition(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `create Deposition - timeout - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            throw TimeoutException()
        }
        runBlocking { zenodo.createDeposition(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `create Deposition - Unauthorized - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Unauthorized.value
            response
        }
        runBlocking { zenodo.createDeposition(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `create Deposition - Forbidden - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Forbidden.value
            response
        }
        runBlocking { zenodo.createDeposition(decodedJWT.subject) }
    }

    @Test(expected = TooManyRetries::class)
    fun `create Deposition - Internal error 500 - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 500
            response
        }
        runBlocking { zenodo.createDeposition(decodedJWT.subject, 4) }
    }

    @Test(expected = TooManyRetries::class)
    fun `create Deposition - Unknown response status - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 300
            response
        }
        runBlocking { zenodo.createDeposition(decodedJWT.subject) }
    }

    @Test
    fun `create upload test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            response
        }
        val result = runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
        assertEquals(Unit, result)
    }

    @Test(expected = MissingOAuthToken::class)
    fun `create upload - missing auth - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns null

        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
    }

    @Test(expected = TooManyRetries::class)
    fun `create upload - Timeout - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            throw TimeoutException()
        }
        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
    }

    @Test(expected = TooManyRetries::class)
    fun `create upload - Unauthorized - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Unauthorized.value
            response
        }
        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
    }

    @Test(expected = TooManyRetries::class)
    fun `create upload - Forbidden - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns HttpStatusCode.Forbidden.value
            response
        }
        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
    }

    @Test(expected = TooManyRetries::class)
    fun `create upload - Internal Server Error 500 - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 500
            response
        }
        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
                , 4
            )
        }
    }

    @Test(expected = TooManyRetries::class)
    fun `create upload - Unknown response status - test`() {
        mockkObject((HttpClient))
        coEvery { oauth.retrieveTokenOrRefresh(any()) } returns oToken
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 300
            response
        }
        runBlocking {
            zenodo.createUpload(
                decodedJWT.subject,
                "depositionID",
                "FileName",
                File("Pathname")
            )
        }
    }

    @Test
    fun `create Authorization URL test`() {
        every { oauth.createAuthorizationUrl(any(), any(), any()) } answers {
            val url = URL("http", "localhost", 5000, "/home")
            url
        }
        assertEquals(
            "http://localhost:5000/home",
            zenodo.createAuthorizationUrl(decodedJWT.subject, "returnTo").toString()
        )
    }

    private val metadata = mapOf("1" to "x", "2" to "y", "-1" to "zz")

    @Test
    fun `zenodoDepositionEntity simple Creation - test`() {
        val entity = ZenodoDepositionEntity(
            "22-2-12",
            listOf(File("path")),
            "1",
            ZenodoDepositionLinks(
                "discard",
                "edit",
                "files",
                "publish",
                "newversion",
                "self"
            ),
            metadata,
            "22-5-12",
            299299,
            2,
            "state",
            true,
            "title"
        )

        assertEquals("22-2-12", entity.created)
        assertEquals("1", entity.id)
        assertEquals("22-5-12", entity.modified)
        assertEquals(299299, entity.owner)
        assertEquals(2, entity.record_id)
        assertEquals("state", entity.state)
        assertTrue(entity.submitted)
        assertEquals("title", entity.title)

        assertEquals("path", entity.files.firstOrNull().toString())

        assertEquals("discard", entity.links.discard)
        assertEquals("edit", entity.links.edit)
        assertEquals("files", entity.links.files)
        assertEquals("publish", entity.links.publish)
        assertEquals("newversion", entity.links.newversion)
        assertEquals("self", entity.links.self)

        assertEquals("x", entity.metadata["1"])

    }
}
