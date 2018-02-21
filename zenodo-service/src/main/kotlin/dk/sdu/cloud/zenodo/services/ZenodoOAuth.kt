package dk.sdu.cloud.zenodo.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.HttpClient
import dk.sdu.cloud.client.asDynamicJson
import java.io.File
import java.net.URL
import java.util.*

private const val SANDBOX_BASE = "https://sandbox.zenodo.org"
private const val PRODUCTION_BASE = "https://zenodo.org"

interface ZenodoOAuthStateStore {
    fun storeStateTokenForUser(cloudUser: String, token: String, returnTo: String)
    fun resolveUserAndRedirectFromStateToken(stateToken: String): Pair<String, String>?

    fun storeAccessAndRefreshToken(cloudUser: String, token: OAuthTokens)
    fun retrieveCurrentTokenForUser(cloudUser: String): OAuthTokens?
}

class InMemoryZenodoOAuthStateStore : ZenodoOAuthStateStore {
    val csrfDb = HashMap<String, Pair<String, String>>()
    val csrfToUser = HashMap<String, String>()
    val tokenDb = HashMap<String, OAuthTokens>()

    companion object {
        private val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }

        fun load(): InMemoryZenodoOAuthStateStore {
            return try {
                mapper.readValue(File("zenodo-oauth.json"))
            } catch (ex: Exception) {
                ex.printStackTrace()
                InMemoryZenodoOAuthStateStore()
            }
        }
    }

    override fun storeStateTokenForUser(cloudUser: String, token: String, returnTo: String) {
        csrfToUser[token] = cloudUser
        csrfDb[cloudUser] = Pair(token, returnTo)
        serialize()
    }

    override fun storeAccessAndRefreshToken(cloudUser: String, token: OAuthTokens) {
        tokenDb[cloudUser] = token
        serialize()
    }

    override fun retrieveCurrentTokenForUser(cloudUser: String): OAuthTokens? {
        return tokenDb[cloudUser]
    }

    override fun resolveUserAndRedirectFromStateToken(stateToken: String): Pair<String, String>? {
        val user = csrfToUser[stateToken] ?: return null
        val (_, returnTo) = csrfDb[user] ?: return null
        csrfToUser.remove(stateToken)
        csrfDb.remove(user)
        return Pair(user, returnTo)
    }

    private fun serialize() {
        File("zenodo-oauth.json").writeText(
            mapper.writeValueAsString(this)
        )
    }
}

class ZenodoOAuth(
    private val clientSecret: String,
    private val clientId: String,
    private val callback: String,
    private val stateStore: ZenodoOAuthStateStore,
    private val useSandbox: Boolean
) {
    val baseUrl: String get() = if (useSandbox) SANDBOX_BASE else PRODUCTION_BASE

    fun createAuthorizationUrl(user: String, returnTo: String, vararg scopes: String): URL = StringBuilder().apply {
        append(baseUrl)
        append("/oauth/authorize?")
        append("response_type=code")

        append('&')
        append("client_id=$clientId")

        append('&')
        append("redirect_uri=$callback")

        val token = UUID.randomUUID().toString()
        stateStore.storeStateTokenForUser(user, token, returnTo)
        append('&')
        append("state=$token")

        if (scopes.isNotEmpty()) {
            append('&')
            append("scope=${scopes.joinToString(" ")}")
        }
    }.let { URL(it.toString()) }

    suspend fun requestTokenWithCode(code: String, state: String): String? {
        val (user, returnTo) = stateStore.resolveUserAndRedirectFromStateToken(state) ?: return null
        val response = HttpClient.post("$baseUrl/oauth/token") {
            addFormParam("client_id", clientId)
            addFormParam("client_secret", clientSecret)
            addFormParam("grant_type", "authorization_code")
            addFormParam("redirect_uri", callback)
            addFormParam("code", code)
        }

        if (response.statusCode !in 200..299) return null
        parseOAuthResponse(response.asDynamicJson()).also {
            if (it != null) stateStore.storeAccessAndRefreshToken(user, it)
        }

        return returnTo
    }

    private suspend fun requestTokenWithRefresh(user: String, refreshToken: String): OAuthTokens? {
        val response = HttpClient.post("$baseUrl/oauth/token") {
            addFormParam("client_id", clientId)
            addFormParam("client_secret", clientSecret)
            addFormParam("grant_type", "refresh_token")
            addFormParam("refresh_token", refreshToken)
        }

        if (response.statusCode !in 200..299) return null
        return parseOAuthResponse(response.asDynamicJson()).also {
            if (it != null) stateStore.storeAccessAndRefreshToken(user, it)
        }
    }

    suspend fun retrieveTokenOrRefresh(user: String): OAuthTokens? {
        val token = stateStore.retrieveCurrentTokenForUser(user)
        if (token != null) {
            if (!token.expired) return token

            return requestTokenWithRefresh(user, token.refreshToken)
        }

        return null
    }

    fun isConnected(user: String): Boolean {
        return stateStore.retrieveCurrentTokenForUser(user) != null
    }
}

data class OAuthTokens(
    val accessToken: String,
    val expiresAt: Long,
    val refreshToken: String
) {
    val expired: Boolean get() = System.currentTimeMillis() > expiresAt
}

private fun parseOAuthResponse(node: JsonNode): OAuthTokens? {
    val accessToken = node["access_token"].asText() ?: return null
    val expiresAt = node["expires_in"].longValue() * 1000 + System.currentTimeMillis()
    val refreshToken = node["refresh_token"].asText() ?: return null

    return OAuthTokens(accessToken, expiresAt, refreshToken)
}
