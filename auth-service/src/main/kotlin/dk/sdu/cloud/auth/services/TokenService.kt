package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

internal typealias JWTAlgorithm = com.auth0.jwt.algorithms.Algorithm

class TokenService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDao: UserDAO<DBSession>,
    private val refreshTokenDao: RefreshTokenDAO<DBSession>,
    private val jwtAlg: JWTAlgorithm,
    private val userCreationService: UserCreationService<*>
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)

    private val secureRandom = SecureRandom()
    private fun generateCsrfToken(): String {
        val array = ByteArray(64)
        secureRandom.nextBytes(array)
        return Base64.getEncoder().encodeToString(array)
    }

    private fun createAccessTokenForExistingSession(user: Principal): AccessToken {
        log.debug("Creating access token for existing session user=$user")
        val currentTimeMillis = System.currentTimeMillis()
        val iat = Date(currentTimeMillis)
        val exp = Date(currentTimeMillis + 1000 * 60 * 30)

        val token = JWT.create().run {
            writeStandardClaims(user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience("api", "irods")
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

    fun createAndRegisterTokenFor(user: Principal): AuthenticationTokens {
        log.debug("Creating and registering token for $user")
        val accessToken = createAccessTokenForExistingSession(user).accessToken
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

    fun logout(refreshToken: String) {
        db.withTransaction {
            if (!refreshTokenDao.delete(it, refreshToken)) throw RefreshTokenException.InvalidToken()
        }
    }

    sealed class RefreshTokenException(why: String, httpStatusCode: HttpStatusCode) :
        RPCException(why, httpStatusCode) {
        class InvalidToken : RefreshTokenException("Invalid token", HttpStatusCode.Unauthorized)
        class InternalError : RefreshTokenException("Internal server error", HttpStatusCode.InternalServerError)
    }
}