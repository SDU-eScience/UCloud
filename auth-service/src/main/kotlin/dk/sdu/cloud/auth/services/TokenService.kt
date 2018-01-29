package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.service.EventProducer
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import dk.sdu.cloud.service.stackTraceToString
import java.util.*

internal typealias JWTAlgorithm = com.auth0.jwt.algorithms.Algorithm

class TokenService(
        private val jwtAlg: JWTAlgorithm,
        private val userEventProducer: EventProducer<String, UserEvent>,
        private val tokenEventProducer: EventProducer<String, RefreshTokenEvent>
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)

    private fun createAccessTokenForExistingSession(user: Principal): AccessToken {
        log.debug("Creating access token for existing session user=$user")
        val iat = Date(System.currentTimeMillis())
        val exp = Date(System.currentTimeMillis() + 1000 * 60 * 30)

        val token = JWT.create().run {
            withSubject(user.id)
            withClaim("role", user.role.name)

            withIssuer("cloud.sdu.dk")
            withExpiresAt(exp)
            withIssuedAt(iat)

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

            sign(jwtAlg)
        }

        return AccessToken(token)
    }

    fun createAndRegisterTokenFor(user: Principal): RequestAndRefreshToken {
        log.debug("Creating and registering token for $user")
        val accessToken = createAccessTokenForExistingSession(user).accessToken
        val refreshToken = UUID.randomUUID().toString()

        launch {
            val createEvent = RefreshTokenEvent.Created(refreshToken, user.id)
            tokenEventProducer.emit(createEvent.key, createEvent)

            val invokeEvent = RefreshTokenEvent.Invoked(refreshToken, accessToken)
            tokenEventProducer.emit(invokeEvent.key, invokeEvent)
        }

        return RequestAndRefreshToken(accessToken, refreshToken)
    }

    fun processSAMLAuthentication(auth: Auth): Person.ByWAYF? {
        try {
            log.debug("Processing SAML response")
            if (auth.authenticated) {
                val id = auth.attributes[AttributeURIs.EduPersonTargetedId]?.firstOrNull() ?:
                        throw IllegalArgumentException("Missing EduPersonTargetedId")

                log.debug("User is authenticated with id $id")

                val existing = UserDAO.findById(id)
                return if (existing == null) {
                    log.debug("User not found. Creating new user...")
                    // In a replay, what do we actually replay? Just the initial requests? Or do we practically turn off
                    // most processing and only replay state changes?
                    // https://softwareengineering.stackexchange.com/questions/310176/event-sourcing-replaying-and-versioning#310323
                    // It seems that we should (and hopefully this is quite close to what we're doing) make a distinction
                    // between requests (commands) and events. Only the events cause state changes.

                    // TODO We need a proper strategy from how to handle replays.
                    // Should this block? Where do we store this in the DB?
                    // From a performance perspective in makes no sense to go through Kafka before we create in DB.
                    // But from a replay perspective we have to do that...
                    val userCreated = PersonUtils.createUserByWAYF(auth)
                    log.debug("userCreated=$userCreated")
                    launch {
                        userEventProducer.emit(id, UserEvent.Created(id, userCreated))
                    }

                    userCreated
                } else {
                    log.debug("User already exists: $existing")
                    existing as Person.ByWAYF
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

    fun refresh(rawToken: String): AccessToken {
        log.debug("Refreshing token: rawToken=$rawToken")
        val token = RefreshTokenAndUserDAO.findById(rawToken) ?: run {
            log.debug("Could not find token!")
            throw RefreshTokenException.InvalidToken()
        }

        val user = UserDAO.findById(token.associatedUser) ?: run {
            log.warn("Received a valid token, but was unable to resolve the associated user: " +
                    token.associatedUser)
            throw RefreshTokenException.InternalError()
        }

        val accessToken = createAccessTokenForExistingSession(user)
        launch {
            val invokeEvent = RefreshTokenEvent.Invoked(rawToken, accessToken.accessToken)
            tokenEventProducer.emit(invokeEvent.key, invokeEvent)
        }

        return accessToken
    }

    fun logout(refreshToken: String) {
        val token = RefreshTokenAndUserDAO.findById(refreshToken) ?: throw RefreshTokenException.InvalidToken()

        launch {
            val event = RefreshTokenEvent.Invalidated(token.token)
            tokenEventProducer.emit(event.key, event)
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