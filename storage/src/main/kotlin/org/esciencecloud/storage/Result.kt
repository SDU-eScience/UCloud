package org.esciencecloud.storage

//
// TODO This really belongs somewhere else, not sure where. Doesn't seem like it needs to be with kafka-common
//
// Doing some experimentation here. It seems, to me at least, that exceptions are very meaningless in a distributed
// system. Here I try to build a few simple Kotlin types that "Results" inspired by those you would find in many
// functional languages. These can either be successful, i.e., Ok(T) or they can be an Error(code, message).
// These seem like more appropriate types to send around in a system. I also try to build them in such a way that
// we still retain much of the convenience we have from exceptions.
//
// We also have to make sure that these error types are easily traceable throughout a system. We need to be able to
// pinpoint why certain errors occur in the system. This becomes, much harder, when we have a distributed system.
//

sealed class Result<out T : Any> {
    companion object {
        private val lastError = object : ThreadLocal<Error<*>>() {
            override fun initialValue(): Error<*> = Error<Any>(-1, "No error set")
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> lastError(): Error<T> = lastError.get() as Error<T>
    }

    fun capture(): T? {
        return when (this) {
            is Ok<T> -> this.result

            is Error<T> -> {
                lastError.set(this)
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> map(mapper: (T) -> R): Result<R> {
        return when (this) {
            is Ok<T> -> Ok(mapper(this.result))
            is Error<T> -> this as Result<R>
        }
    }

    fun orThrow(): T {
        return when (this) {
            is Ok<T> -> result
            is Error<T> -> throw RuntimeException("Error in Result! Error code: $errorCode. Message: $message")
        }
    }

    inline fun onError(handler: (Error<T>) -> Unit) {
        when (this) {
            is Error<T> -> handler(this)
        }
    }
}

// It is always safe to cast an error regardless of the generic
class Error<out T : Any>(
        val errorCode: Int,
        val message: String
) : Result<T>() {
    companion object {
        // TODO Figure out these error codes
        // TODO Some of these might be specific to a product
        fun <T : Any> notFound(message: String = "Entity not found") = Error<T>(123, message)
        fun <T : Any> duplicateResource(message: String = "Entity already exists") = Error<T>(123, message)
        fun <T : Any> permissionDenied(message: String = "Permission denied") = Error<T>(123, message)
        fun <T : Any> invalidMessage(message: String = "Invalid message") = Error<T>(123, message)
    }
}

class Ok<out T : Any>(
        val result: T
) : Result<T>() {
    companion object {
        fun empty(): Ok<Unit> = Ok(Unit)
    }
}
