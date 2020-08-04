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
        block()
    }
}