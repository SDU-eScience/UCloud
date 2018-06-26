package dk.sdu.cloud.storage.util

import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.delay
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

// Copy pasted from master branch of coroutine support. Will come later, won't risk compability issues with ktor

fun ticker(
    delay: Long,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    initialDelay: Long = delay,
    context: CoroutineContext = EmptyCoroutineContext
): ReceiveChannel<Unit> {
    require(delay >= 0) { "Expected non-negative delay, but has $delay" }
    require(initialDelay >= 0) { "Expected non-negative initial delay, but has $initialDelay" }
    return produce(Unconfined + context, capacity = 0) {
        delay(initialDelay, unit)
        while (true) {
            channel.send(Unit)
            delay(delay, unit)
        }
    }
}
