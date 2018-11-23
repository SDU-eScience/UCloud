package dk.sdu.cloud.zenodo.services

import dk.sdu.cloud.service.db.FakeDBSessionFactory
import dk.sdu.cloud.zenodo.util.HttpClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.asynchttpclient.Response
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

val URI.queryParams: Map<String, List<String>>
    get() {
        return query.split("&").map {
            val splitTokensIndex = it.indexOf("=")
            val key = it.substringBefore('=')

            if (splitTokensIndex == -1) Pair(key, "")
            else {
                Pair(key, it.substring(splitTokensIndex + 1))
            }
        }.groupBy({ it.first }, { it.second })
    }


class ZenodoOAuthTest {
    private val clientId = "ClientID"
    private val callback = "callBack"
    private val statesStore = InMemoryZenodoOAuthStateStore()
    private val responseBody = """{"access_token":"access","expires_in":2,"refresh_token":"refreshed"}"""
    private val zenodoAuth = ZenodoOAuth(
        FakeDBSessionFactory,
        "ClientSecret",
        clientId,
        callback,
        statesStore,
        true
    )
    private val user = "user1"
    private val token = "tokenToUser1"
    private val returnTo = "ReturnToString"
    private val oauthToken = OAuthTokens(token, System.currentTimeMillis() + 100000, "refresh")
    private val oauthTokenExpired = OAuthTokens(token, 2, "refresh")


    @Test
    fun `create Authorization Url test`() {
        val result = zenodoAuth.createAuthorizationUrl(
            "user",
            "http://cloud.sdu.dk",
            "scope"
        )

        val params = result.toURI().queryParams
        val state = params["state"]
        assertNotNull(state)
        assertEquals(1, state.size)
        assertTrue(!state.first().isBlank())

        assertEquals(clientId, params["client_id"]?.firstOrNull())
        assertEquals(callback, params["redirect_uri"]?.firstOrNull())
    }

    @Test
    fun `Request Tokens with Code test`() {
        mockkObject(HttpClient)
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            every { response.responseBody } returns responseBody
            response
        }
        statesStore.storeStateTokenForUser(Unit, user, token, returnTo)
        val result = runBlocking { zenodoAuth.requestTokenWithCode("code", token) }
        assertEquals("ReturnToString", result)
    }

    @Test
    fun `Request Tokens with Code - Wrong statusCode - test `() {
        mockkObject(HttpClient)
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 400
            every { response.responseBody } returns responseBody
            response
        }
        statesStore.storeStateTokenForUser(Unit, user, token, returnTo)
        val result = runBlocking { zenodoAuth.requestTokenWithCode("code", token) }
        assertEquals(null, result)
    }

    @Test
    fun `Request Tokens or Refresh test `() {
        mockkObject((HttpClient))
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            every { response.responseBody } returns responseBody
            response
        }
        statesStore.storeStateTokenForUser(Unit, user, token, returnTo)
        statesStore.storeAccessAndRefreshToken(Unit, user, oauthToken)
        val result = runBlocking { zenodoAuth.retrieveTokenOrRefresh(user) }

        assertEquals("tokenToUser1", result?.accessToken)
        assertEquals("refresh", result?.refreshToken)
    }

    @Test
    fun `Request Tokens or Refresh - no token - test `() {
        mockkObject((HttpClient))
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            every { response.responseBody } returns responseBody
            response
        }
        val result = runBlocking { zenodoAuth.retrieveTokenOrRefresh(user) }
        assertEquals(null, result)
    }

    @Test
    fun `Request Tokens or Refresh - token expired - test `() {
        mockkObject((HttpClient))
        coEvery { HttpClient.post(any(), any()) } answers {
            val response = mockk<Response>()
            every { response.statusCode } returns 200
            every { response.responseBody } returns responseBody
            response
        }
        statesStore.storeStateTokenForUser(Unit, user, token, returnTo)
        statesStore.storeAccessAndRefreshToken(Unit, user, oauthTokenExpired)
        val result = runBlocking { zenodoAuth.retrieveTokenOrRefresh(user) }

        assertEquals("access", result?.accessToken)
        assertEquals("refreshed", result?.refreshToken)
    }

    @Test
    fun `is Connected test`() {
        statesStore.storeAccessAndRefreshToken(Unit, user, oauthTokenExpired)

        assertTrue(zenodoAuth.isConnected(user))
    }

    @Test
    fun `is Connected - not true - test`() {
        assertFalse(zenodoAuth.isConnected(user))
    }

    @Test
    fun `Invalidate Token For User test`() {
        statesStore.storeAccessAndRefreshToken(Unit, user, oauthTokenExpired)
        zenodoAuth.invalidateTokenForUser(user)
    }

}
