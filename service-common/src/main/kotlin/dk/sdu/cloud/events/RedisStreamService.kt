package dk.sdu.cloud.events

import io.lettuce.core.Consumer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.StreamMessage
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RedisStreamService(
    private val client: RedisClient,
    private val group: String,
    private val consumerId: String
) : EventStreamService {
    override fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>) {
        val redis = client.connect().async()
        val minimumIdleTime = 60_000 * 5L

        GlobalScope.launch(Dispatchers.IO) {
            val messagesNotYetAcknowledged = HashSet<String>()
            suspend fun sendMessages(messages: List<StreamMessage<String, String>>) {
                val events = messages.map { message ->
                    val textValue = message.body.values.first()
                    stream.deserialize(textValue)
                }

                messagesNotYetAcknowledged.addAll(messages.map { it.id })

                if (consumer.accept(events)) {
                    redis.xackA(stream.name, group, messagesNotYetAcknowledged)
                    messagesNotYetAcknowledged.clear()
                }
            }

            var nextClaim = 0L
            fun setNextClaimTimer() = run { nextClaim = System.currentTimeMillis() + 30_000 }
            setNextClaimTimer()

            while (true) {
                if (System.currentTimeMillis() >= nextClaim) {
                    val pending = redis.xpendingA(stream.name, group)
                    val ids = pending.filter { it.msSinceLastAttempt >= minimumIdleTime }.map { it.id }

                    if (ids.isNotEmpty()) {
                        sendMessages(redis.xclaimA(stream.name, group, consumerId, minimumIdleTime, ids))
                    }

                    setNextClaimTimer()
                } else {
                    sendMessages(
                        redis.xreadgroupA(
                            group, consumerId,
                            XReadArgs.StreamOffset.lastConsumed(stream.name),
                            offset = XReadArgs.Builder.block(2000).count(50)
                        )
                    )
                }
            }
        }
    }

    private suspend fun RedisAsyncCommands<String, String>.xackA(
        stream: String,
        group: String,
        messageIds: Set<String>
    ) = suspendCoroutine<Long> { cont ->
        if (messageIds.isEmpty()) {
            cont.resume(0)
            return@suspendCoroutine
        }

        xack(stream, group, *messageIds.toTypedArray())
            .thenAccept { cont.resume(it) }
            .exceptionally {
                cont.resumeWithException(it)
                null
            }
    }

    private data class PendingMessage(
        val id: String,
        val consumerId: String,
        val msSinceLastAttempt: Long,
        val failureCount: Long
    )

    private suspend fun RedisAsyncCommands<String, String>.xpendingA(
        stream: String,
        group: String,
        limit: Long = 50L
    ) = suspendCoroutine<List<PendingMessage>> { cont ->
        xpending(stream, group, Range.create("-", "+"), Limit.from(limit))
            .thenAccept {
                cont.resume(
                    it.map {
                        val list = it as List<*>
                        PendingMessage(
                            list[0] as String,
                            list[1] as String,
                            list[2] as Long,
                            list[3] as Long
                        )
                    }
                )
            }
            .exceptionally {
                cont.resumeWithException(it)
                null
            }
    }

    private suspend fun RedisAsyncCommands<String, String>.xclaimA(
        stream: String,
        group: String,
        consumerId: String,
        minimumIdleTime: Long,
        ids: List<String>
    ) = suspendCoroutine<List<StreamMessage<String, String>>> { cont ->
        xclaim(stream, Consumer.from(group, consumerId), minimumIdleTime, *ids.toTypedArray())
            .thenAccept { cont.resume(it) }
            .exceptionally {
                cont.resumeWithException(it)
                null
            }
    }

    private suspend fun RedisAsyncCommands<String, String>.xreadgroupA(
        group: String,
        consumerId: String,
        vararg streams: XReadArgs.StreamOffset<String>,
        offset: XReadArgs? = XReadArgs.Builder.block(2000)
    ) = suspendCoroutine<List<StreamMessage<String, String>>> { cont ->
        xreadgroup(Consumer.from(group, consumerId), offset, *streams)
            .thenAccept { cont.resume(it) }
            .exceptionally {
                cont.resumeWithException(it)
                null
            }
    }

    override fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStreams(streams: List<EventStream<*>>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun describeStreams(names: List<String>): Map<String, EventStreamState?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stop() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
