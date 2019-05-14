package dk.sdu.cloud.events

import io.lettuce.core.RedisClient
import kotlinx.coroutines.runBlocking
import java.util.*

data class Foobar(val number: Int)

object FooStreams : EventStreamContainer() {
    val f = stream<Foobar>("foo-stream", keySelector = { it.number.toString() })
}

fun main() {
    val service = RedisStreamService(
        RedisConnectionManager(RedisClient.create("redis://localhost")),
        "my-group",
        UUID.randomUUID().toString(),
        2
    )

    service.subscribe(FooStreams.f, EventConsumer.Immediate {
        println(it)
    })

    val producer = service.createProducer(FooStreams.f)
    runBlocking {
        repeat(10) {
            producer.produce(Foobar(it))
        }
    }

    repeat(5) {
        Thread.sleep(1000)
    }

    service.stop()
}
