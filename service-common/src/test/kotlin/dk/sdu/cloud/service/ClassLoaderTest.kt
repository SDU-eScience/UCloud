package dk.sdu.cloud.service

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) {
    val loader = ClassDiscovery(listOf("dk.sdu.cloud.service"), ClassLoader.getSystemClassLoader()) {
        println(it)
    }
    repeat(3) {
        val start = System.currentTimeMillis()
        runBlocking {
            launch {
                loader.detect()
            }.join()
        }
        println(System.currentTimeMillis() - start)
    }
}
