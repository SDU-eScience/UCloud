package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.apache.kafka.clients.producer.KafkaProducer
import dk.sdu.cloud.auth.AccessToken
import dk.sdu.cloud.auth.RequestAndRefreshToken
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.service.forStream
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

internal typealias JWTAlgorithm = com.auth0.jwt.algorithms.Algorithm

class TokenService(
        private val jwtAlg: JWTAlgorithm,
        eventProducer: KafkaProducer<String, String>
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)

    private val userEventProducer = eventProducer.forStream(AuthStreams.UserUpdateStream)
    private val tokenEventProducer = eventProducer.forStream(AuthStreams.RefreshTokenStream)

    private fun createAccessTokenForExistingSession(user: User): AccessToken {
        val zone = ZoneId.of("GMT")
        val iat = Date.from(LocalDateTime.now().atZone(zone).toInstant())
        val exp = Date.from(LocalDateTime.now().plusMinutes(30).atZone(zone).toInstant())
        val token = JWT.create()
                .withSubject(user.primaryKey)
                .withClaim("role", user.role.name)
                .withClaim("name", user.fullName)
                .withClaim("email", user.email)
                .withIssuer("cloud.sdu.dk")
                .withExpiresAt(exp)
                .withIssuedAt(iat)
                .sign(jwtAlg)

        return AccessToken(token)
    }

    fun createAndRegisterTokenFor(user: User): RequestAndRefreshToken {
        val accessToken = createAccessTokenForExistingSession(user).accessToken
        val refreshToken = UUID.randomUUID().toString()

        launch {
            val createEvent = RefreshTokenEvent.Created(refreshToken, user.primaryKey)
            tokenEventProducer.emit(createEvent.key, createEvent)

            val invokeEvent = RefreshTokenEvent.Invoked(refreshToken, accessToken)
            tokenEventProducer.emit(invokeEvent.key, invokeEvent)
        }

        return RequestAndRefreshToken(accessToken, refreshToken)
    }

    fun processSAMLAuthentication(auth: Auth): User? {
        if (auth.authenticated) {
            // THIS MIGHT NOT BE AN ACTUAL EMAIL
            println("I have received the following attributes:")
            auth.attributes.forEach { k, v -> println("  $k: $v") }

            /*
            I have received the following attributes:
              schacCountryOfCitizenship: [DK, GB]
              preferredLanguage: [en]
              urn:oid:1.3.6.1.4.1.2428.90.1.4: [12345678]
              mail: [lars.larsen@institution.dk]
              norEduPersonLIN: [12345678]
              urn:oid:2.5.4.10: [Institution]
              eduPersonAssurance: [2]
              eduPersonPrimaryAffiliation: [staff]
              eduPersonScopedAffiliation: [staff@testidp.wayf.dk]
              eduPersonTargetedID: [WAYF-DK-5e9d51a044ff4466fab46ad94a758510723baa13]
              schacHomeOrganization: [testidp.wayf.dk]
              eduPersonPrincipalName: [ll@testidp.wayf.dk]
              sn: [Larsen]
              urn:oid:2.5.4.4: [Larsen]
              urn:oid:2.5.4.3: [Lars L]
              urn:oid:2.16.840.1.113730.3.1.39: [en]
              eduPersonEntitlement: [test]
              urn:oid:1.3.6.1.4.1.25178.1.2.15: [urn:mace:terena.org:schac:personalUniqueID:dk:CPR:0304741234]
              organizationName: [Institution]
              urn:oid:0.9.2342.19200300.100.1.3: [lars.larsen@institution.dk]
              gn: [Lars]
              schacPersonalUniqueID: [urn:mace:terena.org:schac:personalUniqueID:dk:CPR:0304741234]
              urn:oid:2.5.4.42: [Lars]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.10: [WAYF-DK-5e9d51a044ff4466fab46ad94a758510723baa13]
              cn: [Lars L]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.11: [2]
              urn:oid:1.3.6.1.4.1.25178.1.2.9: [testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.25178.1.2.5: [DK, GB]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.6: [ll@testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.5: [staff]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.9: [staff@testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.7: [test]
             */

            val email = auth.attributes[AttributeURIs.EduPersonPrincipalName]?.firstOrNull() ?: return null
            val name = auth.attributes[AttributeURIs.CommonName]?.firstOrNull() ?: return null

            val existing = UserDAO.findById(email)
            return if (existing == null) {
                // In a replay, what do we actually replay? Just the initial requests? Or do we practically turn off
                // most processing and only replay state changes?
                // https://softwareengineering.stackexchange.com/questions/310176/event-sourcing-replaying-and-versioning#310323
                // It seems that we should (and hopefully this is quite close to what we're doing) make a distinction
                // between requests (commands) and events. Only the events cause state changes.

                // TODO We need a proper strategy from how to handle replays.
                // Should this block? Where do we store this in the DB?
                // From a performance perspective in makes no sense to go through Kafka before we create in DB.
                // But from a replay perspective we have to do that...
                val userCreated = UserUtils.createUserNoPassword(name, email, Role.USER)
                launch { userEventProducer.emit(email, UserEvent.Created(email, userCreated)) }

                userCreated
            } else {
                existing
            }
        }
        return null
    }

    fun refresh(rawToken: String): AccessToken {
        val token = RefreshTokenAndUserDAO.findById(rawToken) ?: run {
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