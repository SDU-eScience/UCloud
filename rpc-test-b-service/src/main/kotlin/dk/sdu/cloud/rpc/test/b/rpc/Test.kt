package dk.sdu.cloud.rpc.test.b.rpc

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

fun main() {
    repeat(10) {
        val time = measureTimeMillis {
            runBlocking {
                (0 until 100).map {
                    GlobalScope.launch {
                        delay(100)
                    }
                }.joinAll()
            }
        }

        println("Test took $time")
    }
}
