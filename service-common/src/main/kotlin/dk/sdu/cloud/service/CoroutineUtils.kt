package dk.sdu.cloud.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

sealed class ResultOrException<E> {
    data class Result<E>(val result: E) : ResultOrException<E>()
    data class Exception<E>(val throwable: Throwable) : ResultOrException<E>()
}

fun <E> CoroutineScope.safeAsync(coroutine: suspend () -> E): Deferred<ResultOrException<E>> {
    return async {
        try {
            ResultOrException.Result(coroutine())
        } catch (ex: Exception) {
            ResultOrException.Exception<E>(ex)
        }
    }
}

suspend fun <E> Collection<Deferred<ResultOrException<E>>>.awaitAllOrThrow(): List<E> {
    return map {
        val result = it.await()
        when (result) {
            is ResultOrException.Result<E> -> {
                result.result
            }

            is ResultOrException.Exception<E> -> {
                throw result.throwable
            }
        }

    }
}
