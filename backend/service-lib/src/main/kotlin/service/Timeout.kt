package dk.sdu.cloud.service

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

@OptIn(DelicateCoroutinesApi::class)
suspend fun <T> withHardTimeout(
    timeMillis: Long,
    taskInfo: () -> String,
    block: suspend CoroutineScope.() -> T
): T {
    return coroutineScope {
        val done = AtomicBoolean(false)

        GlobalScope.launch {
            delay(timeMillis * 5)
            if (!done.get()) {
                val log = Logger("dk.sdu.cloud.service.withHardTimeout")
                log.error("Could not complete task within 5x ${timeMillis}ms. Information about the task:\n${taskInfo()}")
                exitProcess(1)
            }
        }

        try {
            withTimeout(timeMillis, block)
        } finally {
            done.set(true)
        }
    }
}
