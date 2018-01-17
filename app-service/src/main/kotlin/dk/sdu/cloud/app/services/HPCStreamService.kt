package dk.sdu.cloud.app.services

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.service.*
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.services.HPCStreamServiceKt")
fun <K, T : Any, V : KafkaRequest<T>> KStream<K, V>.authenticate(): AuthenticatedStream<K, T> {
    val branches = mapValues { request ->
        val token: DecodedJWT? = try {
            val validate = TokenValidation.validate(request.header.performedFor)
            log.debug("Validating request: ${request.header}")
            validate
        } catch (ex: JWTVerificationException) {
            log.debug("Rejecting request: ${request.header}")
            null
        }
        Pair(token, request)
    }.branch(
            Predicate { _, value -> value.first != null },
            Predicate { _, value -> value.first == null }
    )

    val authMapped = branches[0].mapValues { RequestAfterAuthentication.Authenticated(it.second, it.first!!) }
    val unauthMapped = branches[1].mapValues { RequestAfterAuthentication.Unauthenticated(it.second) }
    return AuthenticatedStream(authMapped, unauthMapped)
}

data class AuthenticatedStream<K, R : Any>(
        val authenticated: KStream<K, RequestAfterAuthentication.Authenticated<R>>,
        val unauthenticated: KStream<K, RequestAfterAuthentication.Unauthenticated<R>>
) {
    fun <RespondType> respond(
            target: StreamDescription<K, RespondType>,
            onUnauthenticated: (K, RequestAfterAuthentication.Unauthenticated<R>) -> RespondType,
            onAuthenticated: (K, RequestAfterAuthentication.Authenticated<R>) -> RespondType
    ) {
        authenticated.map { k, v -> KeyValue(k, onAuthenticated(k, v)) }.to(target)
        unauthenticated.map { k, v -> KeyValue(k, onUnauthenticated(k, v)) }.to(target)
    }
}

// Not a pretty name, but I couldn't find any name indicating that authentication and taken place that didn't
// also imply that it was successful.
sealed class RequestAfterAuthentication<out T> {
    abstract val originalRequest: KafkaRequest<T>

    val event: T
        get() = originalRequest.event

    val header: RequestHeader
        get() = originalRequest.header

    class Authenticated<out T>(
            override val originalRequest: KafkaRequest<T>,
            val decoded: DecodedJWT
    ) : RequestAfterAuthentication<T>()

    class Unauthenticated<out T>(
            override val originalRequest: KafkaRequest<T>
    ) : RequestAfterAuthentication<T>()
}

