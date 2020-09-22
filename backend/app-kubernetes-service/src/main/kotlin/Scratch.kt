package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.micro.Log4j2ConfigFactory
import dk.sdu.cloud.service.k8.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.apache.logging.log4j.core.config.ConfigurationFactory
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
fun main(): Unit = runBlocking {
    ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    /*
    val client = KubernetesClient()

    coroutineScope {
        val example = URL("https://k8s.io/examples/application/deployment-scale.yaml").readText()
        println(example)
        launch {
            client.watchResource<WatchEvent<Pod>>(this, KubernetesResources.pod).consumeEach {
                println(it.theObject.metadata!!.name)
            }
        }
        client.deleteResource(KubernetesResources.deployment.withName("nginx-deployment"))
        println("No more")
    }
     */

    launch {
        var i = 0
        coroutineScope {
            val countJob = launch {
                var j = 0
                while (isActive) {
                    println("j = ${j++}")
                    delay(1000)
                }
            }

            while (isActive) {
                println("i = ${i++}")
                delay(1000)
                if (i == 10) {
                    countJob.cancel()
                    break
                }
            }
        }
    }.join()

    return@runBlocking
}
