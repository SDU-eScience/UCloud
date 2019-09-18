package dk.sdu.cloud.app.kubernetes.services

import kotlinx.coroutines.*

fun main() {
    runBlocking {
        (0 until 100).map { threadId ->
            GlobalScope.launch(Dispatchers.IO) {
                repeat(100) {
                    Thread.sleep(1000)
                    println("Spinning $threadId")
                }
            }
        }.joinAll()
    }
}