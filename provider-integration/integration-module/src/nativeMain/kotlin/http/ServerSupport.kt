package dk.sdu.cloud.http

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

val middlewares = ArrayList<Middleware>()

fun addMiddleware(middleware: Middleware) {
    middlewares.add(middleware)
}

interface Middleware {
    suspend fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {}
    suspend fun <Req : Any> afterResponse(
        handler: CallHandler<Req, *, *>,
        response: Any?,
        responseCode: HttpStatusCode,
        responseTime: Long
    ) {}
}

data class IngoingCall<ServerCtx>(
    val attributes: AttributeContainer,
    val serverContext: ServerCtx,
)

data class CallWithHandler<R : Any, S : Any, E : Any>(
    val call: CallDescription<R, S, E>,
    val handler: suspend CallHandler<R, S, E>.() -> OutgoingCallResponse<S, E>
) {
    init {
        freeze()
    }
}

class CallHandler<R : Any, S : Any, E : Any> internal constructor(
    val ctx: IngoingCall<*>,
    val request: R,
    val description: CallDescription<R, S, E>
)

sealed class OutgoingCallResponse<S : Any, E : Any> {
    abstract val statusCode: HttpStatusCode

    class Ok<S : Any, E : Any>(
        val result: S,
        override val statusCode: HttpStatusCode = HttpStatusCode.OK
    ) : OutgoingCallResponse<S, E>() {
        override fun toString() = "$statusCode, ${result.toString().take(240)}"
    }

    class Error<S : Any, E : Any>(
        val error: E?,
        override val statusCode: HttpStatusCode
    ) : OutgoingCallResponse<S, E>() {
        override fun toString() = "$statusCode, ${error.toString().take(240)}"
    }

    /**
     * Indicates that content was delivered externally.
     *
     * This means that the content was delivered directly to the underlying medium and does not need to be
     * produced in [IngoingRequestInterceptor.produceResponse].
     */
    class AlreadyDelivered<S : Any, E : Any> : OutgoingCallResponse<S, E>() {
        override val statusCode: HttpStatusCode = HttpStatusCode.OK
    }
}

@Suppress("UNCHECKED_CAST")
val <S : Any> CallHandler<*, S, *>.wsContext: WebSocketContext<*, S, *>
    get() = ctx.serverContext as WebSocketContext<*, S, *>

@SharedImmutable
val bearerKey = AttributeKey<String>("bearer")
var IngoingCall<*>.bearerOrNull: String?
    get() = attributes.getOrNull(bearerKey)
    set(value) {
        attributes.setOrDelete(bearerKey, value)
    }

@SharedImmutable
val securityPrincipalTokenKey = AttributeKey<SecurityPrincipalToken>("principalToken")
var IngoingCall<*>.securityPrincipalTokenOrNull: SecurityPrincipalToken?
    get() = attributes.getOrNull(securityPrincipalTokenKey)
    set(value) {
        attributes.setOrDelete(securityPrincipalTokenKey, value)
    }

val IngoingCall<*>.securityPrincipal: SecurityPrincipal
    get() = securityPrincipalTokenOrNull?.principal ?: error("User is not authenticated")
