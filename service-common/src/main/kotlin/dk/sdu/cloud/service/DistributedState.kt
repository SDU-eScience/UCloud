package dk.sdu.cloud.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.redisConnectionManager
import io.lettuce.core.SetArgs

interface DistributedStateFactory {
    fun <T> create(reader: TypeReference<T>, name: String, expiry: Long? = null): DistributedState<T>
}

class RedisDistributedStateFactory(micro: Micro) : DistributedStateFactory {
    private val connManager = micro.redisConnectionManager

    override fun <T> create(reader: TypeReference<T>, name: String, expiry: Long?): DistributedState<T> {
        return RedisDistributedState(name, expiry, connManager, reader)
    }
}

inline fun <reified T> DistributedStateFactory.create(name: String, expiry: Long? = null): DistributedState<T> {
    return create(jacksonTypeRef(), name, expiry)
}

interface DistributedState<T> {
    val name: String
    val expiry: Long?

    suspend fun get(): T?
    suspend fun set(value: T)
}

class RedisDistributedState<T>(
    override val name: String,
    override val expiry: Long?,
    private val connManager: RedisConnectionManager,
    private val reader: TypeReference<T>
): DistributedState<T> {
    override suspend fun get(): T? {
        val rawValue = connManager.getConnection().get(name).await() ?: return null

        @Suppress("BlockingMethodInNonBlockingContext")
        return defaultMapper.readValue<T>(rawValue, reader)
    }

    override suspend fun set(value: T) {
        val setargs = SetArgs().apply {
            if (expiry != null) {
                px(expiry)
            }
        }

        val serializedValue = defaultMapper.writeValueAsString(value)
        connManager.getConnection().set(name, serializedValue, setargs).await()
    }
}
