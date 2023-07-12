package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncCache<K, V>(
    private val backgroundScope: BackgroundScope,
    val timeToLiveMs: Long = 60_000,
    val deadlineMs: Long = 10_000L,
    private val timeoutException: (key: K) -> Nothing,
    private val retrieve: suspend (key: K) -> V,
) {
    private val mutex = ReadWriterMutex()
    private val cache = HashMap<K, CacheEntry<V>>()
    private val retrieveFn = retrieve

    private data class CacheEntry<V>(
        val retrievedAt: Long,
        @Volatile var value: V?,
        @Volatile var job: Deferred<V>?,
        @Volatile var didFail: Boolean = false
    )

    suspend fun retrieve(key: K): V {
        val entry = mutex.withReader { cache[key] }
        if (entry != null && isAlive(entry)) {
            return awaitEntry(entry)
        }

        val actualEntry = mutex.withWriter {
            val entryAfterMutex = cache[key]
            if (entryAfterMutex != null && isAlive(entryAfterMutex)) {
                entryAfterMutex
            } else {
                val entryToCreate = CacheEntry<V>(Time.now(), null, null)

                entryToCreate.job = backgroundScope.async {
                    val retrievalJob = async { retrieveFn(key) }

                    try {
                        select<V> {
                            retrievalJob.onAwait {
                                entryToCreate.value = it
                                entryToCreate.job = null
                                it
                            }

                            onTimeout(deadlineMs) {
                                retrievalJob.cancel()
                                timeoutException(key)
                            }
                        }
                    } catch (ex: Throwable) {
                        entryToCreate.didFail = true
                        throw ex
                    }
                }

                cache[key] = entryToCreate

                entryToCreate
            }
        }

        return awaitEntry(actualEntry)
    }

    suspend fun invalidate(key: K) {
        mutex.withWriter { cache.remove(key) }
    }

    private fun isAlive(entry: CacheEntry<V>?): Boolean {
        val now = Time.now()
        return entry != null && !entry.didFail && (timeToLiveMs == -1L || now - entry.retrievedAt < timeToLiveMs)
    }

    private suspend fun awaitEntry(entry: CacheEntry<V>): V {
        return entry.value ?: entry.job?.await() ?: error("Internal error in AsyncCache. No value and no job?")
    }

    companion object {
        const val DONT_EXPIRE = -1L
    }
}

suspend fun <K> AsyncCache<K, Boolean>.checkOrRefresh(key: K): Boolean {
    if (retrieve(key)) return true
    invalidate(key)
    return retrieve(key)
}
