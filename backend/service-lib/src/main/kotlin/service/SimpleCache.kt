package dk.sdu.cloud.service

import kotlinx.coroutines.runBlocking
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
    private val onRemove: (suspend (K, V) -> Unit)? = null,
    private val lookup: suspend (K) -> V?,
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
        mutex.withLock {
            val removed = internalMap.remove(key)
            if (removed != null) onRemove?.invoke(key, removed.value)
        }
    }

    override suspend fun insert(key: K, value: V) {
        mutex.withLock {
            internalMap[key] = CacheEntry(Time.now(), value)
        }
    }

    suspend fun transformValue(key: K, transform: (value: V) -> V): V? {
        val existing = get(key) ?: return null
        val transformed = transform(existing)
        insert(key, transformed)
        return transformed
    }

    fun getBlocking(key: K): V? {
        return runBlocking { get(key) }
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

    suspend fun cleanup() {
        if (maxAge == DONT_EXPIRE) return
        val now = Time.now()

        mutex.withLock {
            if (now < nextRemoveExpired) return
            val iterator = internalMap.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.timestamp > maxAge) {
                    iterator.remove()
                    onRemove?.invoke(entry.key, entry.value.value)
                }
            }
        }

        nextRemoveExpired = Time.now() + (maxAge * 5)
    }

    suspend fun findOrNull(predicate: (V) -> Boolean): V? {
        cleanup()

        val cacheEntry = mutex.withLock {
            internalMap.values.find { predicate(it.value) }
        } ?: return null

        if (maxAge == DONT_EXPIRE) return cacheEntry.value
        if (Time.now() - cacheEntry.timestamp < maxAge) return cacheEntry.value
        return null
    }

    companion object : Loggable {
        override val log = logger()
        const val DONT_EXPIRE = -1L

        val allCachesOnlyForTestingPlease = ArrayList<WeakReference<SimpleCache<*, *>>>()
    }
}
