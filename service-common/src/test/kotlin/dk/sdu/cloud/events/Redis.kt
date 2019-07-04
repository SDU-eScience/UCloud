package dk.sdu.cloud.events

import dk.sdu.cloud.service.Loggable
import io.lettuce.core.RedisClient
import kotlinx.coroutines.runBlocking
import java.lang.IllegalStateException
import java.util.*
import kotlin.random.Random

data class Foo(val foobar: String)

object FooStreams : EventStreamContainer() {
    val myStream = stream<Foo>("my-stream", { it.foobar })
}

object L : Loggable {
    override val log = logger()
}

fun main() {
    val redis = RedisStreamService(
        RedisConnectionManager(RedisClient.create("redis://localhost")),
        "foo-group",
        UUID.randomUUID().toString(),
        1
    )

    var i = 0
    redis.subscribe(FooStreams.myStream, EventConsumer.Batched(maxLatency = 500L, maxBatchSize = 1000) {
        L.log.info("$it")
        i++

        if (i == 1) {
            println("Hitting this again?")
            throw IllegalStateException(System.currentTimeMillis().toString())
        }
    })

    while (true) {
        val producer = redis.createProducer(FooStreams.myStream)
        runBlocking {
            repeat(10) {
                producer.produce(Foo("$it"))
                println("HI!")
            }
        }
        Thread.sleep(1000)
    }
}
