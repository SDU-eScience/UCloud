package dk.sdu.cloud.service

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.RedisConnectionManager
import io.lettuce.core.pubsub.RedisPubSubAdapter

interface BroadcastingStream{
    suspend fun <T : Any> subscribe(stream: EventStream<T>, handler: (T) -> Unit)
    suspend fun <T : Any> unsubscribe(stream: EventStream<T>)
    suspend fun <T : Any> broadcast(message: T, stream: EventStream<T>)
}

class RedisBroadcastingStream(
    private val connManager: RedisConnectionManager
) : BroadcastingStream {
    override suspend fun <T : Any> subscribe(stream: EventStream<T>, handler: (T) -> Unit) {
        connManager.getPubSubConnection().addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String?, message: String?) {
                if (channel == stream.name && message != null) {
                    val deserializedMessage = runCatching {
                        stream.deserialize(message)
                    }.getOrNull() ?: return

                    handler(deserializedMessage)
                }
            }
        })

        connManager.getPubSubConnection().async().subscribe(stream.name).await()
    }

    override suspend fun <T : Any> unsubscribe(stream: EventStream<T>) {
        connManager.getPubSubConnection().async().unsubscribe(stream.name)
    }

    override suspend fun <T : Any> broadcast(message: T, stream: EventStream<T>) {
        connManager.getConnection().publish(stream.name, defaultMapper.writeValueAsString(message))
    }
}
