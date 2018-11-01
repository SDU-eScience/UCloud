package dk.sdu.cloud.service

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
