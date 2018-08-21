package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.util.*

internal typealias JWTAlgorithm = com.auth0.jwt.algorithms.Algorithm

class TokenService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDao: UserDAO<DBSession>,
    private val refreshTokenDao: RefreshTokenDAO<DBSession>,
    private val jwtAlg: JWTAlgorithm,
    private val userEventProducer: MappedEventProducer<String, UserEvent>,
    private val tokenEventProducer: MappedEventProducer<String, RefreshTokenEvent>,
    private val ottEventProducer: MappedEventProducer<String, OneTimeTokenEvent>
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)

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

    fun createAndRegisterTokenFor(user: Principal): RequestAndRefreshToken {
        log.debug("Creating and registering token for $user")
        val accessToken = createAccessTokenForExistingSession(user).accessToken
        val refreshToken = UUID.randomUUID().toString()

        launch {
            val createEvent = RefreshTokenEvent.Created(refreshToken, user.id)
            tokenEventProducer.emit(createEvent)

            val invokeEvent = RefreshTokenEvent.Invoked(refreshToken, accessToken)
            tokenEventProducer.emit(invokeEvent)
        }

        return RequestAndRefreshToken(accessToken, refreshToken)
    }

    fun processSAMLAuthentication(samlRequestProcessor: SamlRequestProcessor): Person.ByWAYF? {
        try {
            log.debug("Processing SAML response")
            if (samlRequestProcessor.authenticated) {
                val id =
                    samlRequestProcessor.attributes[AttributeURIs.EduPersonTargetedId]?.firstOrNull() ?: throw IllegalArgumentException(
                        "Missing EduPersonTargetedId"
                    )

                log.debug("User is authenticated with id $id")

                return try {
                    db.withTransaction { userDao.findById(it, id) } as Person.ByWAYF
                } catch (ex: UserException.NotFound) {
                    log.debug("User not found. Creating new user...")

                    val userCreated = PersonUtils.createUserByWAYF(samlRequestProcessor)
                    log.debug("userCreated=$userCreated")
                    launch {
                        userEventProducer.emit(UserEvent.Created(id, userCreated))
                    }

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

        val oneTimeToken = createOneTimeAccessTokenForExistingSession(user, *audience)
        launch {
            val invokeEvent = RefreshTokenEvent.InvokedOneTime(
                jwt, audience.toList(),
                oneTimeToken.accessToken
            )
            tokenEventProducer.emit(invokeEvent)

            val createdEvent = OneTimeTokenEvent.Created(oneTimeToken.jti)
            ottEventProducer.emit(createdEvent)
        }

        return oneTimeToken
    }

    fun refresh(rawToken: String): AccessToken {
        val accessToken = db.withTransaction { session ->
            log.debug("Refreshing token: rawToken=$rawToken")
            val token = refreshTokenDao.findById(session, rawToken) ?: run {
                log.debug("Could not find token!")
                throw RefreshTokenException.InvalidToken()
            }

            val user = userDao.findByIdOrNull(session, token.associatedUser) ?: run {
                log.warn(
                    "Received a valid token, but was unable to resolve the associated user: " +
                            token.associatedUser
                )
                throw RefreshTokenException.InternalError()
            }

            createAccessTokenForExistingSession(user)
        }

        launch {
            val invokeEvent = RefreshTokenEvent.Invoked(rawToken, accessToken.accessToken)
            tokenEventProducer.emit(invokeEvent)
        }
        return accessToken
    }

    fun logout(refreshToken: String) {
        val token = db.withTransaction {
            refreshTokenDao.findById(it, refreshToken) ?: throw RefreshTokenException.InvalidToken()
        }

        launch {
            val event = RefreshTokenEvent.Invalidated(token.token)
            tokenEventProducer.emit(event)
        }
    }

    sealed class RefreshTokenException : Exception() {
        abstract val httpCode: HttpStatusCode

        class InvalidToken : RefreshTokenException() {
            override val httpCode = HttpStatusCode.Unauthorized
        }

        class InternalError : RefreshTokenException() {
            override val httpCode = HttpStatusCode.InternalServerError
        }
    }
}