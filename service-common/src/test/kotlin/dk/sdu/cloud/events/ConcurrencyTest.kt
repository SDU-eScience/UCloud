package dk.sdu.cloud.events

import dk.sdu.cloud.micro.RedisFeature
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.redisConnectionManager
import dk.sdu.cloud.service.RedisBroadcastingStream
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.retrySection
import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

@Ignore("Test suite is not automatic")
class ConcurrencyTest {
    private val micro = initializeMicro().apply {
        install(RedisFeature)
    }
    private val redis = micro.eventStreamService
    private val redisConnectionManager = micro.redisConnectionManager
    private val broadcastingStream = RedisBroadcastingStream(redisConnectionManager)

    data class Message(val message: String)

    object Streams : EventStreamContainer() {
        val test = stream<Message>("redis-concurrency-test", { "" })
        val broadcast = stream<Message>("redis-concurrency-broadcast", { "" })
    }

    @Test
    @Ignore
    fun `test crashing a bunch of coroutines`() {
        val counter = AtomicInteger(0)
        RedisScope.start()
        repeat(10_000) { id ->
            RedisScope.launch {
                if (id % 10 == 0) {
                    throw IllegalStateException()
                }

                counter.incrementAndGet()
            }
        }

        while (true) {
            val count = counter.get()
            println(count)
            Thread.sleep(1000)
            if (count == 9000) break
        }
    }

    @Test
    @Ignore
    fun `test blocking in consumer`() {
        assertTrue { redis is RedisStreamService }
        val producer = redis.createProducer(Streams.test)
        val consumed = AtomicInteger(0)

        val times = 500
        val job = RedisScope.launch {
            repeat(times) {
                println("Producing next message $it")
                try {
                    producer.produce(Message("hello $it"))
                } catch (ex: Throwable) {
                    println("This correctly crashed")
                }
                delay(1000)
            }
        }

        redis.subscribe(Streams.test, EventConsumer.Immediate {
            Thread.sleep(1_000)
            println("Consumed next message: $it")
            consumed.incrementAndGet()
        })

        while (consumed.get() < times) {
            Thread.sleep(100)
        }
    }

    @Test
    fun `test crashing broadcaster`() {
        val counter = AtomicInteger()
        RedisScope.start()

        runBlocking {
            try {
                broadcastingStream.subscribe(Streams.broadcast) {
                    println("Consuming next message: $it")
                    counter.incrementAndGet()
                    //throw IllegalStateException()
                }
            } catch (ex: Throwable) {
                println("I didn't expect this")
            }
        }

        RedisScope.launch {
            repeat(100) {
                retrySection(attempts = 50, delay = 1000) {
                    println("Sending message $it")
                    broadcastingStream.broadcast(Message("Hello new $it"), Streams.broadcast)
                    delay(1000)
                }
            }
        }

        while (counter.get() < 100) Thread.sleep(100)
    }
}
