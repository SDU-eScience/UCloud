package org.esciencecloud.services

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.apache.zookeeper.ZooDefs
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    runBlocking {
        val serviceDefinition = ServiceDefinition("foo", "1.0.0")
        val instance = ServiceInstance(serviceDefinition, "localhost", 1337)
        val zk = ZooKeeperConnection(listOf(ZooKeeperHostInfo("localhost"))).connect()

        val node = zk.registerService(instance, ZooDefs.Ids.OPEN_ACL_UNSAFE)
        println("Got back: $node")
        delay(5, TimeUnit.SECONDS)
        println("Ready!")
        zk.markServiceAsReady(node, instance)
        delay(5, TimeUnit.SECONDS)
        println("Going down!")
        zk.markServiceAsStopping(node, instance)
        delay(5, TimeUnit.SECONDS)
        println("Disconnecting!")
    }
}