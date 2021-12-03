package dk.sdu.cloud.http

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import io.ktor.http.*
import kotlinx.cinterop.CPointer
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

val h2oServer = AtomicReference<H2OServer?>(null)
val middlewares = AtomicReference<List<Middleware>>(emptyList())

fun addMiddleware(middleware: Middleware) {
    while (true) {
        val current = middlewares.value
        val newList = ArrayList(current).also {
            it.add(middleware)
            it.freeze()
        }

        if (middlewares.compareAndSet(current, newList)) break
    }
}

interface Middleware {
    fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>)
}

data class HttpContext(
    val reqPtr: CPointer<h2o.h2o_req_t>
)

data class IngoingCall<ServerCtx>(
    val attributes: AttributeContainer,
    val serverContext: ServerCtx,
)

data class CallWithHandler<R : Any, S : Any, E : Any>(
    val call: CallDescription<R, S, E>,
    val handler: CallHandler<R, S, E>.() -> OutgoingCallResponse<S, E>
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
