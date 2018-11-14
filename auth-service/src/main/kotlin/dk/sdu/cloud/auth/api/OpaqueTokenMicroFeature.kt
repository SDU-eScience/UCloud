package dk.sdu.cloud.auth.api

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.MicroAttributeKey
import dk.sdu.cloud.service.MicroFeature
import dk.sdu.cloud.service.MicroFeatureFactory
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.TokenValidationException
import dk.sdu.cloud.service.TokenValidationFeature
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.tokenValidation
import kotlinx.coroutines.runBlocking

class OpaqueTokenMicroFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(RefreshingJWTCloudFeature)
        ctx.requireFeature(TokenValidationFeature)

        ctx.tokenValidation.addToChain(0, OpaqueTokenValidation(ctx.refreshingJwtCloud))
    }

    companion object : MicroFeatureFactory<OpaqueTokenMicroFeature, Unit> {
        override val key: MicroAttributeKey<OpaqueTokenMicroFeature> = MicroAttributeKey("opaque-token-feature")
        override fun create(config: Unit): OpaqueTokenMicroFeature = OpaqueTokenMicroFeature()
    }
}

class OpaqueTokenValidation(val cloud: AuthenticatedCloud) : TokenValidation<SecurityPrincipalToken> {
    override val tokenType: Class<SecurityPrincipalToken> = SecurityPrincipalToken::class.java

    private data class CacheEntry(val token: SecurityPrincipalToken, val expiresAt: Long)

    private val cache = HashMap<String, CacheEntry>()

    private fun addCacheEntry(token: String, contents: SecurityPrincipalToken) {
        synchronized(this) {
            cacheCleanup()

            val entry = CacheEntry(contents, System.currentTimeMillis() + 1000L * 60 * 1)
            cache[token] = entry
        }
    }

    private fun cacheCleanup() {
        val now = System.currentTimeMillis()
        val it = cache.iterator()
        while (it.hasNext()) {
            val (_, entry) = it.next()
            if (now > entry.expiresAt) it.remove()
        }
    }

    private fun resolveFromCache(token: String): SecurityPrincipalToken? {
        return synchronized(this) {
            cache[token]?.takeIf { System.currentTimeMillis() < it.expiresAt }?.token
        }
    }

    override fun decodeToken(token: SecurityPrincipalToken): SecurityPrincipalToken {
        return token
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): SecurityPrincipalToken {
        if (!token.startsWith(OPAQUE_TOKEN_PREFIX)) throw TokenValidationException.Invalid()

        // We cache entries for a minute to ensure we don't make multiple calls during a single request
        val fromCache = resolveFromCache(token)
        if (fromCache != null) return fromCache

        return runBlocking {
            AuthDescriptions.verifyToken.call(VerifyTokenRequest(token), cloud).orThrow()
        }.also { addCacheEntry(token, it) }
    }
}

internal const val OPAQUE_TOKEN_PREFIX = "cloud-"
