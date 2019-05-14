package dk.sdu.cloud.events

import io.lettuce.core.RedisClient
import java.util.*

data class Foobar(val number: Int)

object FooStreams : EventStreamContainer() {
    val f = stream<Foobar>("foo-stream", keySelector = { it.number.toString() })
}

fun main() {
    val service = RedisStreamService(RedisClient.create("redis://localhost"), "my-group", UUID.randomUUID().toString())
    service.subscribe(FooStreams.f, EventConsumer.Immediate {
        println(it)
    })

    while (true) {
        Thread.sleep(1000)
    }
}
