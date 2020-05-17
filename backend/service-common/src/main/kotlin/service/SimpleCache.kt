package dk.sdu.cloud.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimpleCache<K, V>(
    private val maxAge: Long = 60_000,
    private val lookup: suspend (K) -> V?
) {
    private data class CacheEntry<V>(val timestamp: Long, val value: V)

    private val internalMap = HashMap<K, CacheEntry<V>>()
    private val mutex = Mutex()
    private var nextRemoveExpired = System.currentTimeMillis() + (maxAge * 5)

    suspend fun clearAll() {
        mutex.withLock { internalMap.clear() }
    }

    suspend fun get(key: K): V? {
        cleanup()

        val existing = mutex.withLock {
            internalMap[key]
        }

        if (existing != null) {
            if (maxAge == DONT_EXPIRE) {
                return existing.value
            } else if (System.currentTimeMillis() - existing.timestamp < maxAge) {
                return existing.value
            }
        }

        val result = lookup(key) ?: return null
        mutex.withLock {
            internalMap[key] = CacheEntry(System.currentTimeMillis(), result)
        }

        return result
    }

    private suspend fun cleanup() {
        if (maxAge == DONT_EXPIRE) return
        val now = System.currentTimeMillis()

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

        nextRemoveExpired = System.currentTimeMillis() + (maxAge * 5)
    }

    companion object {
        const val DONT_EXPIRE = -1L
    }
}