package dk.sdu.cloud.events

import io.lettuce.core.RedisClient

data class Foobar(val number: Int)

object FooStreams : EventStreamContainer() {
    val f = stream<Foobar>("my-stream", keySelector = { it.number.toString() })
}

fun main() {
    val service = RedisStreamService(RedisClient.create("redis://localhost"))
    service.subscribe(FooStreams.f, EventConsumer.Immediate {
        println(it)
    })

    while (true) {
        Thread.sleep(1000)
    }
}