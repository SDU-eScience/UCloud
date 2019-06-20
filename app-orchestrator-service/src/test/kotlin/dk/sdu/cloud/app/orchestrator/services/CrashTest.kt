package dk.sdu.cloud.app.orchestrator.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    OrchestrationScope.init()
    GlobalScope.launch {
        println("hi!")
    }

    runBlocking {
        runCatching {
            OrchestrationScope.launch {
                throw IllegalStateException()
            }.join()
        }
    }

    OrchestrationScope.launch {
        println("Hello")
    }

    Thread.sleep(10000)
    OrchestrationScope.stop()
}
