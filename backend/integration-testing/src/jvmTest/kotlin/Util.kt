package dk.sdu.cloud.integration

import dk.sdu.cloud.service.SystemTimeProvider
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.runBlocking

/**
 * Utility function for integration tests. This will automatically wipe the database of UCloud.
 *
 * This allows developers to write tests in the following way:
 *
 * ```kotlin
 * @Test
 * fun myTest() = t {
 *  // suspending code
 * }
 * ```
 */
fun t(block: suspend () -> Unit) {
    Time.provider = SystemTimeProvider

    runBlocking {
        UCloudLauncher.wipeDatabases()
        UCloudLauncher.runExtraInitializations()
        block()
    }
}

/**
 * Utility code for retrying a section multiple times. This is useful for testing async code.
 */
inline fun <T> retrySection(attempts: Int = 5, delay: Long = 500, block: () -> T): T {
    for (i in 1..attempts) {
        @Suppress("TooGenericExceptionCaught")
        try {
            return block()
        } catch (ex: Throwable) {
            if (i == attempts) throw ex
            Thread.sleep(delay)
        }
    }
    throw IllegalStateException("retrySection impossible situation reached. This should not happen.")
}
