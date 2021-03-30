package dk.sdu.cloud.file.ucloud.services

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

class WorkQueue<T>(
    private val dispatcher: CoroutineDispatcher,
    private val doWork: suspend WorkQueue<T>.(nextItem: T) -> Unit,
) {
    val channel = Channel<T>(Channel.UNLIMITED)

    suspend fun start(numberOfCoroutines: Int) {
        val idleCount = AtomicInteger(0)

        coroutineScope {
            (0 until numberOfCoroutines).map {
                launch(dispatcher) {
                    var didReportIdle = false
                    while (isActive) {
                        val nextItem = channel.poll()
                        if (nextItem == null) {
                            // NOTE(Dan): We don't know if we have run out of work or if there are simply no more work
                            // right now. To deal with this, we keep a count of idle workers. If all workers report that
                            // they are idle, then we must have run out of work.
                            val newIdleCount =
                                if (!didReportIdle) {
                                    didReportIdle = true
                                    idleCount.incrementAndGet()
                                } else {
                                    idleCount.get()
                                }

                            if (newIdleCount == numberOfCoroutines) {
                                break // break before the delay
                            }
                            delay(10)
                            continue
                        }

                        doWork(nextItem)
                    }
                }
            }.joinAll()
        }
    }
}

suspend fun <T> runWork(
    dispatcher: CoroutineDispatcher,
    numberOfCoroutines: Int,
    initialWorkload: Collection<T>,
    doWork: suspend WorkQueue<T>.(nextItem: T) -> Unit
) {
    val queue = WorkQueue(dispatcher, doWork)
    for (item in initialWorkload) queue.channel.send(item)
    queue.start(numberOfCoroutines)
}
