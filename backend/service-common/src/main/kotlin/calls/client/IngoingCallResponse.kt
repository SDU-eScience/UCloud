package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

sealed class IngoingCallResponse<S : Any, E : Any> {
    abstract val statusCode: HttpStatusCode

    data class Ok<S : Any, E : Any>(val result: S, override val statusCode: HttpStatusCode) :
        IngoingCallResponse<S, E>()

    data class Error<S : Any, E : Any>(val error: E?, override val statusCode: HttpStatusCode) :
        IngoingCallResponse<S, E>()
}

fun <T : Any> IngoingCallResponse<T, *>.orThrow(): T {
    if (this !is IngoingCallResponse.Ok) {
        throw RPCException.fromStatusCode(statusCode)
    }
    return result
}

fun <T : Any, E : Any> IngoingCallResponse<T, E>.orRethrowAs(rethrow: (IngoingCallResponse.Error<T, E>) -> Nothing): T {
    when (this) {
        is IngoingCallResponse.Ok -> {
            return result
        }

        is IngoingCallResponse.Error -> {
            rethrow(this)
        }
    }
}

fun <T : Any, E : Any> IngoingCallResponse<T, E>.throwIfInternal(): IngoingCallResponse<T, E> {
    if (statusCode.value in 500..599) throw RPCException.fromStatusCode(statusCode)
    return this
}

fun <T : Any, E : Any> IngoingCallResponse<T, E>.throwIfInternalOrBadRequest(): IngoingCallResponse<T, E> {
    if (statusCode.value in 500..599 || statusCode.value == 400) {
        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }
    return this
}

fun <T : Any> IngoingCallResponse<T, *>.orNull(): T? {
    if (this !is IngoingCallResponse.Ok) {
        return null
    }
    return result
}
