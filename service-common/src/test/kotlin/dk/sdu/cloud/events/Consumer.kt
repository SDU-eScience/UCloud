package dk.sdu.cloud.events

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.initWithDefaultFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

data class Message(val message: String)
object ServiceD : ServiceDescription {
    override val version: String = "1"
    override val name: String = "d"
}

object Streams : EventStreamContainer() {
    val stream = stream<Message>("Foobar", { it.message })
}

fun main() {
    val micro = Micro()
    micro.initWithDefaultFeatures(ServiceD, arrayOf("--dev", "--config-dir", "/Users/danthrane/sducloud"))
    val streamService = micro.eventStreamService

    streamService.subscribe(Streams.stream, EventConsumer.Batched(maxLatency = 5000) {
        println("Running code!")
        println(it)
    })

    runBlocking {
        launch {
            try {
                streamService.start()
            } catch (ex: Exception) {
                println("BAD")
                exitProcess(1)
            }
        }

        while (true) {
            println("We can still run code!")
            delay(1000)
        }
    }
}
