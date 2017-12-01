package org.esciencecloud.services

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    runBlocking {
        val serviceDefinition = ServiceDefinition("foo", "1.0.0")
        val zk = ZooKeeperConnection(listOf(ZooKeeperHostInfo("localhost"))).connect()

        while (true) {
            zk.listServicesWithStatus(serviceDefinition.name, serviceDefinition.version).forEach {
                println(it)
            }
            println("---")

            delay(1, TimeUnit.SECONDS)
        }
    }
}