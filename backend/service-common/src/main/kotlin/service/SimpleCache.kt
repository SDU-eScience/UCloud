package dk.sdu.cloud.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

interface Cache<K, V : Any> {
    suspend fun clearAll()

    suspend fun remove(key: K)

    suspend fun get(key: K): V?

    suspend fun insert(key: K, value: V)
}

class SimpleCache<K, V : Any>(
    private val maxAge: Long = 60_000,
    private val lookup: suspend (K) -> V?
) : Cache<K, V> {
    private data class CacheEntry<V>(val timestamp: Long, val value: V)

    private val internalMap = HashMap<K, CacheEntry<V>>()
    private val mutex = Mutex()
    private var nextRemoveExpired = Time.now() + (maxAge * 5)

    init {
        allCachesOnlyForTestingPlease.add(WeakReference(this))
    }

    override suspend fun clearAll() {
        mutex.withLock { internalMap.clear() }
    }

    override suspend fun remove(key: K) {
        mutex.withLock { internalMap.remove(key) }
    }

    override suspend fun insert(key: K, value: V) {
        mutex.withLock {
            internalMap[key] = CacheEntry(Time.now(), value)
        }
    }

    override suspend fun get(key: K): V? {
        cleanup()

        val existing = mutex.withLock {
            internalMap[key]
        }

        if (existing != null) {
            if (maxAge == DONT_EXPIRE) {
                return existing.value
            } else if (Time.now() - existing.timestamp < maxAge) {
                return existing.value
            }
        }

        val result = lookup(key) ?: return null
        mutex.withLock {
            internalMap[key] = CacheEntry(Time.now(), result)
        }

        return result
    }

    private suspend fun cleanup() {
        if (maxAge == DONT_EXPIRE) return
        val now = Time.now()

        mutex.withLock {
            if (now < nextRemoveExpired) return
            val iterator = internalMap.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.timestamp > maxAge) {
                    iterator.remove()
                }
            }
        }

        nextRemoveExpired = Time.now() + (maxAge * 5)
    }

    companion object : Loggable {
        override val log = logger()
        const val DONT_EXPIRE = -1L

        val allCachesOnlyForTestingPlease = ArrayList<WeakReference<SimpleCache<*, *>>>()
    }
}
