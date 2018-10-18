package dk.sdu.cloud.zenodo.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.zenodo.util.HttpClient
import dk.sdu.cloud.zenodo.util.asDynamicJson
import java.io.File
import java.net.URL
import java.util.UUID

interface ZenodoOAuthStateStore<Session> {
    fun storeStateTokenForUser(session: Session, cloudUser: String, token: String, returnTo: String)
    fun resolveUserAndRedirectFromStateToken(session: Session, stateToken: String): Pair<String, String>?

    fun storeAccessAndRefreshToken(session: Session, cloudUser: String, token: OAuthTokens)
    fun retrieveCurrentTokenForUser(session: Session, cloudUser: String): OAuthTokens?

    fun invalidateUser(session: Session, cloudUser: String)
}

class InMemoryZenodoOAuthStateStore : ZenodoOAuthStateStore<Unit> {
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

    override fun storeStateTokenForUser(session: Unit, cloudUser: String, token: String, returnTo: String) {
        csrfToUser[token] = cloudUser
        csrfDb[cloudUser] = Pair(token, returnTo)
        serialize()
    }

    override fun storeAccessAndRefreshToken(session: Unit, cloudUser: String, token: OAuthTokens) {
        tokenDb[cloudUser] = token
        serialize()
    }

    override fun retrieveCurrentTokenForUser(session: Unit, cloudUser: String): OAuthTokens? {
        return tokenDb[cloudUser]
    }

    override fun resolveUserAndRedirectFromStateToken(session: Unit, stateToken: String): Pair<String, String>? {
        val user = csrfToUser[stateToken] ?: return null
        val (_, returnTo) = csrfDb[user] ?: return null
        csrfToUser.remove(stateToken)
        csrfDb.remove(user)
        return Pair(user, returnTo)
    }

    override fun invalidateUser(session: Unit, cloudUser: String) {
        tokenDb.remove(cloudUser)
        serialize()
    }

    private fun serialize() {
        File("zenodo-oauth.json").writeText(
            mapper.writeValueAsString(this)
        )
    }
}
private const val SANDBOX_BASE = "https://sandbox.zenodo.org"
private const val PRODUCTION_BASE = "https://zenodo.org"
private const val OK_STATUSCODE_START = 200
private const val OK_STATUSCODE_END = 299

class ZenodoOAuth<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val clientSecret: String,
    private val clientId: String,
    private val callback: String,
    private val stateStore: ZenodoOAuthStateStore<DBSession>,
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
        db.withTransaction {
            stateStore.storeStateTokenForUser(it, user, token, returnTo)
        }
        append('&')
        append("state=$token")

        if (scopes.isNotEmpty()) {
            append('&')
            append("scope=${scopes.joinToString(" ")}")
        }
    }.let { URL(it.toString()) }

    suspend fun requestTokenWithCode(code: String, state: String): String? {
        val (user, returnTo) = db.withTransaction {
            stateStore.resolveUserAndRedirectFromStateToken(it, state) ?: return null
        }
        val response = HttpClient.post("$baseUrl/oauth/token") {
            addFormParam("client_id", clientId)
            addFormParam("client_secret", clientSecret)
            addFormParam("grant_type", "authorization_code")
            addFormParam("redirect_uri", callback)
            addFormParam("code", code)
        }

        if (response.statusCode !in OK_STATUSCODE_START..OK_STATUSCODE_END) return null
        parseOAuthResponse(response.asDynamicJson()).also { resp ->
            if (resp != null) db.withTransaction { stateStore.storeAccessAndRefreshToken(it, user, resp) }
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

        if (response.statusCode !in OK_STATUSCODE_START..OK_STATUSCODE_END) return null
        return parseOAuthResponse(response.asDynamicJson()).also { resp ->
            if (resp != null) db.withTransaction { stateStore.storeAccessAndRefreshToken(it, user, resp) }
        }
    }

    suspend fun retrieveTokenOrRefresh(user: String): OAuthTokens? {
        val token = db.withTransaction { stateStore.retrieveCurrentTokenForUser(it, user) }
        if (token != null) {
            if (!token.expired) return token

            return requestTokenWithRefresh(user, token.refreshToken)
        }

        return null
    }

    fun invalidateTokenForUser(user: String) {
        db.withTransaction {
            stateStore.invalidateUser(it, user)
        }
    }

    fun isConnected(user: String): Boolean {
        return db.withTransaction { stateStore.retrieveCurrentTokenForUser(it, user) != null }
    }
}

data class OAuthTokens(
    val accessToken: String,
    val expiresAt: Long,
    val refreshToken: String
) {
    val expired: Boolean get() = System.currentTimeMillis() > expiresAt
}

private const val TO_MILLS = 1000

private fun parseOAuthResponse(node: JsonNode): OAuthTokens? {
    val accessToken = node["access_token"].asText() ?: return null
    val expiresAt = node["expires_in"].longValue() * TO_MILLS + System.currentTimeMillis()
    val refreshToken = node["refresh_token"].asText() ?: return null

    return OAuthTokens(accessToken, expiresAt, refreshToken)
}
