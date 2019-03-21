package dk.sdu.cloud.events

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.initWithDefaultFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.system.exitProcess

fun main() {
    val micro = Micro()
    micro.initWithDefaultFeatures(ServiceD, arrayOf("--dev", "--config-dir", "/Users/danthrane/sducloud"))
    val streamService = micro.eventStreamService

    val producer = streamService.createProducer(Streams.stream)

    runBlocking {
        while (true) {
            println("New message!")
            try {
                producer.produce(Message("Hi ${Date().toGMTString()}"))
            } catch (ex: Exception) {
                println("BAD")
                exitProcess(1)
            }
            delay(1000)
        }
    }
}
