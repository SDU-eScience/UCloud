package dk.sdu.cloud.calls.client

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpStatusCode

object FakeOutgoingCall : OutgoingCall {
    override val attributes: AttributeContainer = AttributeContainer()
}

sealed class IngoingCallResponse<S : Any, E : Any> : DocVisualizable {
    abstract val statusCode: HttpStatusCode
    abstract val ctx: OutgoingCall

    data class Ok<S : Any, E : Any>(
        val result: S,
        override val statusCode: HttpStatusCode,
        override val ctx: OutgoingCall,
    ) : IngoingCallResponse<S, E>() {
        override fun visualize(): DocVisualization = DocVisualization.Card(
            "$statusCode",
            emptyList(),
            listOf(visualizeValue(result))
        )
    }

    data class Error<S : Any, E : Any>(
        val error: E?,
        override val statusCode: HttpStatusCode,
        override val ctx: OutgoingCall,
    ) : IngoingCallResponse<S, E>() {
        override fun visualize(): DocVisualization = DocVisualization.Card(
            "$statusCode",
            emptyList(),
            if (error != null) listOf(visualizeValue(error))
            else listOf(DocVisualization.Inline("No error information")),
        )
    }
}

fun <T : Any> IngoingCallResponse<T, *>.orThrow(): T {
    if (this !is IngoingCallResponse.Ok) {
        if (this is IngoingCallResponse.Error) {
            val error = this.error
            if (error is CommonErrorMessage) {
                throw RPCException(error.why, statusCode, error.errorCode)
            }
        }
        throw RPCException.fromStatusCode(statusCode)
    }
    return result
}

fun <T : Any> IngoingCallResponse<T, *>.throwError(): Nothing {
    orThrow()
    throw IllegalStateException("Attempted to throw on a call which was successful: $this")
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
