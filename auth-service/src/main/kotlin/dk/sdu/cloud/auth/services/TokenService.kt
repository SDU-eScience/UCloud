package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.http.CoreAuthController.Companion.MAX_EXTENSION_TIME_IN_MS
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.requireScope
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.security.cert.Extension
import java.util.*

internal typealias JWTAlgorithm = com.auth0.jwt.algorithms.Algorithm

class TokenService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDao: UserDAO<DBSession>,
    private val refreshTokenDao: RefreshTokenDAO<DBSession>,
    private val jwtAlg: JWTAlgorithm,
    private val userCreationService: UserCreationService<*>,
    private val allowedServiceExtensionScopes: Map<String, Set<SecurityScope>> = emptyMap()
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)

    private val secureRandom = SecureRandom()
    private fun generateCsrfToken(): String {
        val array = ByteArray(64)
        secureRandom.nextBytes(array)
        return Base64.getEncoder().encodeToString(array)
    }

    private fun createAccessTokenForExistingSession(
        user: Principal,
        expiresIn: Long = 1000 * 60 * 10
    ): AccessToken {
        log.debug("Creating access token for existing session user=$user")
        val currentTimeMillis = System.currentTimeMillis()
        val iat = Date(currentTimeMillis)
        val exp = Date(currentTimeMillis + expiresIn)

        val token = JWT.create().run {
            writeStandardClaims(user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience("api")
            sign(jwtAlg)
        }

        return AccessToken(token)
    }

    private fun createOneTimeAccessTokenForExistingSession(
        user: Principal,
        vararg audience: String
    ): OneTimeAccessToken {
        val currentTimeMillis = System.currentTimeMillis()
        val iat = Date(currentTimeMillis)
        val exp = Date(currentTimeMillis + 1000 * 30)
        val jti = UUID.randomUUID().toString()

        val token = JWT.create().run {
            writeStandardClaims(user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience(*audience)
            withJWTId(jti)
            sign(jwtAlg)
        }

        return OneTimeAccessToken(token, jti)
    }

    private fun JWTCreator.Builder.writeStandardClaims(user: Principal) {
        withSubject(user.id)
        withClaim("role", user.role.name)

        withIssuer("cloud.sdu.dk")

        when (user) {
            is Person -> {
                withClaim("firstNames", user.firstNames)
                withClaim("lastName", user.lastName)
                if (user.orcId != null) withClaim("orcId", user.orcId)
                if (user.title != null) withClaim("title", user.title)
            }
        }

        // TODO This doesn't seem right
        val type = when (user) {
            is Person.ByWAYF -> "wayf"
            is Person.ByPassword -> "password"
            is ServicePrincipal -> "service"
        }
        withClaim("principalType", type)
    }

    private fun createExtensionToken(
        user: Principal,
        expiresIn: Long,
        scopes: List<SecurityScope>,
        requestedBy: String
    ): AccessToken {
        val iat = Date(System.currentTimeMillis())
        val exp = Date(System.currentTimeMillis() + expiresIn)

        return AccessToken(
            JWT.create().run {
                writeStandardClaims(user)
                withIssuedAt(iat)
                withExpiresAt(exp)
                withClaim(CLAIM_EXTENDED_BY, requestedBy)
                withAudience(*scopes.map { it.toString() }.toTypedArray())
                sign(jwtAlg)
            }
        )
    }

    fun createAndRegisterTokenFor(
        user: Principal,
        expiresIn: Long = 1000 * 60 * 10
    ): AuthenticationTokens {
        log.debug("Creating and registering token for $user")
        val accessToken = createAccessTokenForExistingSession(user, expiresIn).accessToken
        val refreshToken = UUID.randomUUID().toString()
        val csrf = generateCsrfToken()

        val tokens = AuthenticationTokens(accessToken, refreshToken, csrf)
        db.withTransaction {
            val tokenAndUser = RefreshTokenAndUser(user.id, tokens.refreshToken, tokens.csrfToken)
            log.debug(tokenAndUser.toString())
            refreshTokenDao.insert(it, tokenAndUser)
        }

        return tokens
    }

    fun extendToken(
        token: SecurityPrincipalToken,
        expiresIn: Long,
        rawSecurityScopes: List<String>,
        requestedBy: String
    ): AccessToken {
        // We must ensure that the token we receive has enough permissions. For this we require all:write.
        // This is needed since we would otherwise have privilege escalation here
        token.requireScope(SecurityScope.ALL_WRITE)

        val requestedScopes = rawSecurityScopes.map {
            try {
                SecurityScope.parseFromString(it)
            } catch (ex: IllegalArgumentException) {
                throw ExtensionException.BadRequest("Bad scope: $it")
            }
        }

        // Request and scope validation
        val extensions = allowedServiceExtensionScopes[requestedBy] ?: emptySet()
        if (!requestedScopes.all { it in extensions }) {
            throw ExtensionException.Unauthorized(
                "Service $requestedBy is not allowed to ask for one " +
                        "of the requested permissions"
            )

        }

        if (expiresIn < 0 || expiresIn > MAX_EXTENSION_TIME_IN_MS) {
            throw ExtensionException.BadRequest("Bad request (expiresIn)")
        }

        // Find user
        val user = db.withTransaction {
            userDao.findByIdOrNull(it, token.principal.username)
        } ?: throw ExtensionException.InternalError("Could not find user in database")

        return createExtensionToken(user, expiresIn, requestedScopes, requestedBy)
    }

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

    fun requestOneTimeToken(jwt: String, vararg audience: String): OneTimeAccessToken {
        log.debug("Requesting one-time token: audience=$audience jwt=$jwt")

        val validated = TokenValidation.validateOrNull(jwt) ?: throw RefreshTokenException.InvalidToken()
        val user =
            db.withTransaction {
                userDao.findByIdOrNull(it, validated.subject) ?: throw RefreshTokenException.InternalError()
            }

        return createOneTimeAccessTokenForExistingSession(user, *audience)
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

            val newCsrf = generateCsrfToken()
            refreshTokenDao.updateCsrf(session, rawToken, newCsrf)
            val accessToken = createAccessTokenForExistingSession(user)
            AccessTokenAndCsrf(accessToken.accessToken, newCsrf)
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

    sealed class ExtensionException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
        class BadRequest(why: String) : ExtensionException(why, HttpStatusCode.BadRequest)
        class Unauthorized(why: String) : ExtensionException(why, HttpStatusCode.Unauthorized)
        class InternalError(why: String) : ExtensionException(why, HttpStatusCode.InternalServerError)
    }

    sealed class RefreshTokenException(why: String, httpStatusCode: HttpStatusCode) :
        RPCException(why, httpStatusCode) {
        class InvalidToken : RefreshTokenException("Invalid token", HttpStatusCode.Unauthorized)
        class InternalError : RefreshTokenException("Internal server error", HttpStatusCode.InternalServerError)
    }

    companion object {
        const val CLAIM_EXTENDED_BY = "extendedBy"
    }
}