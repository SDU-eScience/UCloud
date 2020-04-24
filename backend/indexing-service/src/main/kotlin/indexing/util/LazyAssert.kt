package dk.sdu.cloud.indexing.util

@PublishedApi
internal object LazyAssert {
    @PublishedApi
    internal val ENABLED: Boolean = javaClass.desiredAssertionStatus()
}

inline fun lazyAssert(message: (() -> String) = { "Assertion failed" }, assertion: () -> Boolean) {
    if (LazyAssert.ENABLED) {
        if (!assertion()) {
            val resolvedMessage = message()
            throw AssertionError(resolvedMessage)
        }
    }
}

inline fun lazyAssert(message: String, assertion: () -> Boolean) {
    if (LazyAssert.ENABLED) {
        if (!assertion()) {
            throw AssertionError(message)
        }
    }
}
