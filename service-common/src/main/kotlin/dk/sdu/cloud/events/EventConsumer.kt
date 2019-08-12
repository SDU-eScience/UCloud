package dk.sdu.cloud.events

import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.Exception

// New and improved API. It is a lot more simple and does everything we need it to do. Will also make it easier
// to convert for new backends.

sealed class EventConsumer<V> {
    // TODO RedisStreamService is depending on only Immediate and Batched to exist to perform processing in a
    //  separate coroutine. Make sure it is updated before introducing new ones.

    abstract suspend fun accept(events: List<V>): Boolean

    class Immediate<V>(
        private val handler: suspend (V) -> Unit
    ) : EventConsumer<V>() {
        override suspend fun accept(events: List<V>): Boolean {
            @Suppress("TooGenericExceptionCaught")
            return try {
                coroutineScope {
                    val jobs = events.map { launch { handler(it) } }
                    jobs.joinAll()

                    events.isNotEmpty()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }
        }
    }

    class Batched<V>(
        private val maxLatency: Long = 500,
        private val maxBatchSize: Int = 1000,
        private val handler: suspend (List<V>) -> Unit
    ) : EventConsumer<V>() {
        private val internalBatch = ArrayList<V>()
        private var nextEmit = System.currentTimeMillis() + maxLatency
        private val lock = Mutex()

        private fun resetState() {
            internalBatch.clear()
            nextEmit = System.currentTimeMillis() + maxLatency
        }

        override suspend fun accept(events: List<V>): Boolean {
            lock.withLock {
                internalBatch.addAll(events)

                if (internalBatch.size >= maxBatchSize) {
                    internalBatch.chunked(maxBatchSize).forEach { handler(it) }
                    resetState()
                    return true
                }

                if (System.currentTimeMillis() > nextEmit && internalBatch.isNotEmpty()) {
                    handler(internalBatch)
                    resetState()
                    return true
                }

                return false
            }
        }
    }
}
