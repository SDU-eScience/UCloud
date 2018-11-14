package dk.sdu.cloud.auth.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AccessTokenAndCsrf
import dk.sdu.cloud.auth.api.AccessTokenContents
import dk.sdu.cloud.auth.api.AuthenticationTokens
import dk.sdu.cloud.auth.api.OneTimeAccessToken
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.TokenType
import dk.sdu.cloud.auth.http.CoreAuthController.Companion.MAX_EXTENSION_TIME_IN_MS
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.toSecurityToken
import java.security.SecureRandom
import java.util.*

class TokenService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDao: UserDAO<DBSession>,
    private val refreshTokenDao: RefreshTokenDAO<DBSession>,
    private val jwtFactory: JWTFactory,
    private val opaqueTokenFactory: OpaqueTokenService<DBSession>,
    private val userCreationService: UserCreationService<*>,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val allowedServiceExtensionScopes: Map<String, Set<SecurityScope>> = emptyMap()
) {
    private val secureRandom = SecureRandom()

    private fun resolveTokenGenerator(tokenType: TokenType): TokenGenerationService = when (tokenType) {
        TokenType.JWT -> jwtFactory
        TokenType.OPAQUE -> opaqueTokenFactory
    }

    private fun createAccessTokenForExistingSession(
        user: Principal,
        sessionReference: String?,
        expiresIn: Long = TEN_MIN_IN_MILLS,
        tokenType: TokenType = TokenType.JWT
    ): AccessToken {
        val token = AccessTokenContents(
            user = user,
            scopes = listOf(SecurityScope.ALL_WRITE),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + expiresIn,
            claimableId = null,
            sessionReference = sessionReference,
            extendedBy = null
        )
        return AccessToken(resolveTokenGenerator(tokenType).generate(token))
    }

    private fun createOneTimeAccessTokenForExistingSession(
        user: Principal,
        audience: List<SecurityScope>,
        tokenType: TokenType = TokenType.JWT
    ): OneTimeAccessToken {
        val jti = UUID.randomUUID().toString()
        val token = AccessTokenContents(
            user = user,
            scopes = audience,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + THIRTY_SECONDS_IN_MILLS,
            claimableId = jti
        )

        return OneTimeAccessToken(resolveTokenGenerator(tokenType).generate(token), jti)
    }

    private fun createExtensionToken(
        user: Principal,
        expiresIn: Long,
        scopes: List<SecurityScope>,
        requestedBy: String,
        tokenType: TokenType = TokenType.JWT
    ): AccessToken {
        val token = AccessTokenContents(
            user = user,
            scopes = scopes,
            createdAt = System.currentTimeMillis(),
            expiresAt = if (expiresIn != -1L) System.currentTimeMillis() + expiresIn else null,
            extendedBy = requestedBy
        )

        return AccessToken(resolveTokenGenerator(tokenType).generate(token))
    }

    fun createAndRegisterTokenFor(
        user: Principal,
        expiresIn: Long = TEN_MIN_IN_MILLS
    ): AuthenticationTokens {
        fun generateCsrfToken(): String {
            val array = ByteArray(CSRF_TOKEN_SIZE)
            secureRandom.nextBytes(array)
            return Base64.getEncoder().encodeToString(array)
        }

        log.debug("Creating and registering token for $user")
        val refreshToken = UUID.randomUUID().toString()
        val csrf = generateCsrfToken()

        val tokenAndUser = RefreshTokenAndUser(user.id, refreshToken, csrf)
        db.withTransaction {
            log.debug(tokenAndUser.toString())
            refreshTokenDao.insert(it, tokenAndUser)
        }

        val accessToken = createAccessTokenForExistingSession(
            user,
            tokenAndUser.publicSessionReference,
            expiresIn
        ).accessToken

        return AuthenticationTokens(accessToken, refreshToken, csrf)
    }

    fun extendToken(
        token: SecurityPrincipalToken,
        expiresIn: Long,
        rawSecurityScopes: List<String>,
        requestedBy: String,
        tokenType: TokenType = TokenType.JWT
    ): AccessToken {
        val requestedScopes = rawSecurityScopes.map {
            try {
                SecurityScope.parseFromString(it)
            } catch (ex: IllegalArgumentException) {
                log.debug(ex.stackTraceToString())
                throw ExtensionException.BadRequest("Bad scope: $it")
            }
        }

        // Request and scope validation
        val extensions = allowedServiceExtensionScopes[requestedBy] ?: emptySet()
        val allRequestedScopesAreCoveredByPolicy = requestedScopes.all { requestedScope ->
            extensions.any { userScope ->
                requestedScope.isCoveredBy(userScope)
            }
        }
        if (!allRequestedScopesAreCoveredByPolicy) {
            throw ExtensionException.Unauthorized(
                "Service $requestedBy is not allowed to ask for one " +
                        "of the requested permissions. We were asked for: $requestedScopes, " +
                        "but service is only allowed to $extensions"
            )
        }

        // We must ensure that the token we receive has enough permissions.
        // This is needed since we would otherwise have privilege escalation here
        val allRequestedScopesAreCoveredByUserScopes = requestedScopes.all { requestedScope ->
            token.scopes.any { userScope ->
                requestedScope.isCoveredBy(userScope)
            }
        }

        if (!allRequestedScopesAreCoveredByUserScopes) {
            throw ExtensionException.Unauthorized("Cannot extend due to missing user scopes")
        }

        if (tokenType == TokenType.JWT) {
            if (expiresIn < 0 || expiresIn > MAX_EXTENSION_TIME_IN_MS) {
                throw ExtensionException.BadRequest("Bad request (expiresIn)")
            }
        }

        // Require, additionally, that no all or special scopes are requested
        val noSpecialScopes = requestedScopes.all {
            it.segments.first() != SecurityScope.ALL_SCOPE &&
                    it.segments.first() != SecurityScope.SPECIAL_SCOPE
        }

        if (!noSpecialScopes) {
            throw ExtensionException.Unauthorized("Cannot request special scopes")
        }

        // Find user
        val user = db.withTransaction {
            userDao.findByIdOrNull(it, token.principal.username)
        } ?: throw ExtensionException.InternalError("Could not find user in database")

        return createExtensionToken(user, expiresIn, requestedScopes, requestedBy, tokenType)
    }

    fun requestOneTimeToken(jwt: String, audience: List<SecurityScope>): OneTimeAccessToken {
        log.debug("Requesting one-time token: audience=$audience jwt=$jwt")

        val validated = tokenValidation.validateOrNull(jwt) ?: throw RefreshTokenException.InvalidToken()
        val user = db.withTransaction {
            userDao.findByIdOrNull(it, validated.subject) ?: throw RefreshTokenException.InternalError()
        }

        val currentScopes = validated.toSecurityToken().scopes
        val allScopesCovered = audience.all { requestedScope ->
            currentScopes.any { requestedScope.isCoveredBy(it) }
        }

        if (!allScopesCovered) {
            log.debug("We were asked to cover $audience, but the token only covers $currentScopes")
            throw RefreshTokenException.InvalidToken()
        }

        return createOneTimeAccessTokenForExistingSession(user, audience)
    }

    fun refresh(rawToken: String, csrfToken: String? = null): AccessTokenAndCsrf {
        return db.withTransaction { session ->
            log.debug("Refreshing token: rawToken=$rawToken")
            val token = refreshTokenDao.findById(session, rawToken) ?: run {
                log.debug("Could not find token!")
                throw RefreshTokenException.InvalidToken()
            }

            if (csrfToken != null && csrfToken != token.csrf) {
                log.info("Invalid CSRF token")
                log.debug("Received token: $csrfToken, but I expected ${token.csrf}")
                throw RefreshTokenException.InvalidToken()
            }

            val user = userDao.findByIdOrNull(session, token.associatedUser) ?: run {
                log.warn(
                    "Received a valid token, but was unable to resolve the associated user: " +
                            token.associatedUser
                )
                throw RefreshTokenException.InternalError()
            }

            val accessToken = createAccessTokenForExistingSession(user, token.publicSessionReference)
            AccessTokenAndCsrf(accessToken.accessToken, token.csrf)
        }
    }

    fun logout(refreshToken: String, csrfToken: String? = null) {
        db.withTransaction {
            if (csrfToken == null) {
                if (!refreshTokenDao.delete(it, refreshToken)) throw RefreshTokenException.InvalidToken()
            } else {
                val userAndToken =
                    refreshTokenDao.findById(it, refreshToken) ?: throw RefreshTokenException.InvalidToken()
                if (csrfToken != userAndToken.csrf) throw RefreshTokenException.InvalidToken()
                if (!refreshTokenDao.delete(it, refreshToken)) throw RefreshTokenException.InvalidToken()
            }
        }
    }

    // TODO Should be moved to SAML package
    fun processSAMLAuthentication(samlRequestProcessor: SamlRequestProcessor): Person.ByWAYF? {
        try {
            log.debug("Processing SAML response")
            if (samlRequestProcessor.authenticated) {
                val id =
                    samlRequestProcessor.attributes[AttributeURIs.EduPersonTargetedId]?.firstOrNull()
                        ?: throw IllegalArgumentException(
                            "Missing EduPersonTargetedId"
                        )

                log.debug("User is authenticated with id $id")

                return try {
                    db.withTransaction { userDao.findById(it, id) } as Person.ByWAYF
                } catch (ex: UserException.NotFound) {
                    log.debug("User not found. Creating new user...")

                    val userCreated = PersonUtils.createUserByWAYF(samlRequestProcessor)
                    userCreationService.blockingCreateUser(userCreated)
                    userCreated
                }
            }
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException -> {
                    log.info("Illegal incoming SAML message")
                    log.debug(ex.stackTraceToString())
                }
                else -> {
                    log.warn("Caught unexpected exception while processing SAML response:")
                    log.warn(ex.stackTraceToString())
                }
            }
        }

        return null
    }

    companion object : Loggable {
        private const val TEN_MIN_IN_MILLS = 1000 * 60 * 10L
        private const val THIRTY_SECONDS_IN_MILLS = 1000 * 60L
        private const val CSRF_TOKEN_SIZE = 64

        override val log = logger()
    }
}
