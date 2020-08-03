package dk.sdu.cloud.integration

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
    runBlocking {
        UCloudLauncher.wipeDatabases()
        block()
    }
}