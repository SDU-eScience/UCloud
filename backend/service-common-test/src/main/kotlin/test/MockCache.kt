package dk.sdu.cloud.test

import dk.sdu.cloud.service.Cache

class MockCache<K, V : Any> : Cache<K, V> {
    private val backingMap = HashMap<K, V>()

    override suspend fun clearAll() {
        backingMap.clear()
    }

    override suspend fun remove(key: K) {
        backingMap.remove(key)
    }

    override suspend fun get(key: K): V? {
        return backingMap[key]
    }

    fun put(key: K, value: V) {
        backingMap[key] = value
    }
}
