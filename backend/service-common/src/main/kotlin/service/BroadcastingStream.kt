package dk.sdu.cloud.service

import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.events.RedisScope
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.launch

/**
 * Broadcasting streams allows a set of live subscribers to receive messages from other members. All subscribers
 * will receive all messages.
 *
 * This works significantly different from normal event streams which will load-balance the messages between members
 * of a service. In a [BroadcastingStream] all members will receive all events. Offline members are never notified
 * about these events.
 */
interface BroadcastingStream {
    suspend fun <T : Any> subscribe(stream: EventStream<T>, handler: suspend (T) -> Unit)
    suspend fun <T : Any> unsubscribe(stream: EventStream<T>)
    suspend fun <T : Any> broadcast(message: T, stream: EventStream<T>)
}

/**
 * A redis based implementation of [BroadcastingStream].
 */
class RedisBroadcastingStream(
    private val connManager: RedisConnectionManager
) : BroadcastingStream {
    override suspend fun <T : Any> subscribe(stream: EventStream<T>, handler: suspend (T) -> Unit) {
        connManager.getPubSubConnection().addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String?, message: String?) {
                if (channel == stream.name && message != null) {
                    val deserializedMessage = runCatching {
                        stream.deserialize(message)
                    }.getOrNull() ?: return

                    RedisScope.launch {
                        handler(deserializedMessage)
                    }
                }
            }
        })

        connManager.getPubSubConnection().async().subscribe(stream.name).await()
    }

    override suspend fun <T : Any> unsubscribe(stream: EventStream<T>) {
        connManager.getPubSubConnection().async().unsubscribe(stream.name)
    }

    override suspend fun <T : Any> broadcast(message: T, stream: EventStream<T>) {
        connManager.getConnection().publish(stream.name, stream.serialize(message))
    }
}
