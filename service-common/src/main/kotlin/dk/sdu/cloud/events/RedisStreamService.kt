package dk.sdu.cloud.events

import io.lettuce.core.Consumer
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

class RedisStreamService(private val client: RedisClient) : EventStreamService {
    override fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>) {
        val connection = client.connect()
        val async: RedisAsyncCommands<String, String>?
        async = connection.async()

        GlobalScope.launch(Dispatchers.IO) {
            // TODO We should use xpending to retrieve stale jobs and claim them with xclaim.
            //  These should then go into the normal consumer
            while (true) {
                println("Spinning!")
                consumer.accept(
                    async.xreadgroupA(
                        XReadArgs.StreamOffset.lastConsumed("my-stream"),
                        offset = XReadArgs.Builder.block(2000).count(50)
                    ).map { message ->
                        val textValue = message.body.values.first()
                        stream.deserialize(textValue)
                    }
                )
            }
        }
    }

    private suspend fun RedisAsyncCommands<String, String>.xreadgroupA(
        vararg streams: XReadArgs.StreamOffset<String>,
        offset: XReadArgs? = XReadArgs.Builder.block(2000)
    ) = suspendCoroutine<List<StreamMessage<String, String>>> { cont ->
        xreadgroup(Consumer.from("my-group", UUID.randomUUID().toString()), offset, *streams)
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