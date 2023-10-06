package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.ReadWriterMutex
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncCache<K, V>(
    private val backgroundScope: BackgroundScope,
    val timeToLiveMilliseconds: Long = 60_000,
    val timeoutMilliseconds: Long = 10_000L,
    val fetchEagerly: Boolean = false,
    private val timeoutException: (key: K) -> Nothing,
    private val retrieve: suspend (key: K) -> V,
) {
    private val mutex = ReadWriterMutex()
    private val prefetchList = HashSet<K>()
    private val cache = HashMap<K, CacheEntry<V>>()
    private val retrieveFn = retrieve

    init {
        if (fetchEagerly) {
            backgroundScope.launch {
                while (isActive) {
                    val toRefetch = ArrayList<K>()
                    mutex.withReader {
                        for (item in prefetchList) {
                            val now = Time.now()
                            val entry = cache[item]
                            val needsRefetching =
                                if (entry != null) {
                                    now - entry.retrievedAt > timeToLiveMilliseconds &&
                                            timeToLiveMilliseconds != DONT_EXPIRE
                                } else {
                                    true
                                }

                            if (needsRefetching) toRefetch.add(item)
                        }
                    }

                    if (toRefetch.isNotEmpty()) {
                        for (key in toRefetch) {
                            mutex.withWriter {
                                val currentValue: V? = cache[key]?.value
                                cache[key] = launchJobButIRequireExistingMutex(key, currentValue)
                            }
                        }
                    }

                    delay(50)
                }
            }
        }
    }

    suspend fun prefetch(key: K) {
        mutex.withWriter {
            if (prefetchList.add(key)) {
                cache[key] = launchJobButIRequireExistingMutex(key)
            }
        }
    }

    private data class CacheEntry<V>(
        val retrievedAt: Long,
        @Volatile var value: V?,
        @Volatile var job: Deferred<V>?,
        @Volatile var didFail: Boolean = false
    )

    suspend fun retrieve(key: K): V {
        if (fetchEagerly) {
            val fast = retrieveNowOrNull(key)
            if (fast != null) return fast
            val entry = mutex.withReader { cache[key] }
            if (entry != null) {
                return coroutineScope {
                    val retrievalJob = async { entry.job?.await() }
                    try {
                        select<V> {
                            retrievalJob.onAwait {
                                it ?: entry.value ?: timeoutException(key)
                            }

                            onTimeout(timeoutMilliseconds) {
                                retrievalJob.cancel()
                                timeoutException(key)
                            }
                        }
                    } catch (ex: Throwable) {
                        val value = entry.value
                        if (value != null) value
                        else timeoutException(key)
                    }
                }
            } else {
                prefetch(key)
                return retrieve(key)
            }
        }

        val entry = mutex.withReader { cache[key] }
        if (entry != null && isAlive(entry)) {
            return awaitEntry(entry)
        }

        val actualEntry = mutex.withWriter {
            val entryAfterMutex = cache[key]
            if (entryAfterMutex != null && isAlive(entryAfterMutex)) {
                entryAfterMutex
            } else {
                launchJobButIRequireExistingMutex(key).also {
                    cache[key] = it
                }
            }
        }

        return awaitEntry(actualEntry)
    }

    private fun launchJobButIRequireExistingMutex(key: K, existingValue: V? = null): CacheEntry<V> {
        val entryToCreate = CacheEntry<V>(Time.now(), existingValue, null)

        entryToCreate.job = backgroundScope.async {
            val retrievalJob = async { retrieveFn(key) }

            try {
                select<V> {
                    retrievalJob.onAwait {
                        entryToCreate.value = it
                        entryToCreate.job = null
                        it
                    }

                    onTimeout(timeoutMilliseconds) {
                        retrievalJob.cancel()
                        timeoutException(key)
                    }
                }
            } catch (ex: Throwable) {
                entryToCreate.didFail = true
                if (fetchEagerly) {
                    log.warn("Caught exception while resolving cache entry: ${ex.toReadableStacktrace()}")
                }
                throw ex
            }
        }

        return entryToCreate
    }

    suspend fun retrieveNowOrNull(key: K): V? {
        val entry = mutex.withReader { cache[key] }
        if (entry != null) {
            return entry.value
        }
        return null
    }

    suspend fun invalidate(key: K) {
        mutex.withWriter {
            cache.remove(key)
            prefetchList.remove(key)
        }
    }

    private fun isAlive(entry: CacheEntry<V>?): Boolean {
        if (fetchEagerly) return true

        val now = Time.now()
        return entry != null && !entry.didFail && (timeToLiveMilliseconds == -1L || now - entry.retrievedAt < timeToLiveMilliseconds)
    }

    private suspend fun awaitEntry(entry: CacheEntry<V>): V {
        return entry.value ?: entry.job?.await() ?: error("Internal error in AsyncCache. No value and no job?")
    }

    companion object : Loggable {
        const val DONT_EXPIRE = -1L
        override val log = logger()
    }
}

suspend fun <K> AsyncCache<K, Boolean>.checkOrRefresh(key: K): Boolean {
    if (retrieve(key)) return true
    invalidate(key)
    return retrieve(key)
}
