package dk.sdu.cloud.integration

import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.service.test.assertThatInstance

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

fun IngoingCallResponse<*, *>.assertUserError() {
    assertThatInstance(this, "should fail with a user error") { it.statusCode.value in 400..499 }
}
