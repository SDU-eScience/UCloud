package dk.sdu.cloud

import kotlinx.coroutines.delay

suspend inline fun <T> withRetries(maxAttempts: Int, timeToSleep: Long = 0L, fn: (attempt: Int) -> T): T {
    check(maxAttempts >= 1)
    for (i in 0 until maxAttempts) {
        try {
            return fn(i)
        } catch (ex: Throwable) {
            if (i == maxAttempts - 1) {
                throw ex
            }
            delay(timeToSleep)
        }
    }
    error("should not happen")
}