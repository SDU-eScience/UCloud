package dk.sdu.cloud.file.util

inline fun <R> retryWithCatch(
    maxRetries: Int = 5,
    retryDelayInMs: Long = 50,
    exceptionFilter: (Exception) -> Boolean = { true },
    body: () -> R
): R {
    val suppressed = ArrayList<Exception>()

    for (i in 1..maxRetries) {
        try {
            return body()
        } catch (ex: Exception) {
            if (exceptionFilter(ex)) {
                suppressed.add(ex)
            } else {
                throw ex
            }
        }
        Thread.sleep(retryDelayInMs)
    }

    throw TooManyRetries(suppressed)
}

class TooManyRetries(val causes: List<Exception>) : RuntimeException("Too many retries", causes.first())
