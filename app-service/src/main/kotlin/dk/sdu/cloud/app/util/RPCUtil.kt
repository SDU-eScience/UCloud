package dk.sdu.cloud.app.util

import dk.sdu.cloud.calls.client.IngoingCallResponse

inline fun <T : Any, E : Any> IngoingCallResponse<T, E>.orThrowOnError(
    onError: (IngoingCallResponse.Error<T, E>) -> Nothing
): IngoingCallResponse.Ok<T, E> {
    return when (this) {
        is IngoingCallResponse.Ok -> this
        is IngoingCallResponse.Error -> onError(this)
        else -> throw IllegalStateException()
    }
}