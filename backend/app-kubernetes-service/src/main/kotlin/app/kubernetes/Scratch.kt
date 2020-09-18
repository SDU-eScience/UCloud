package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.micro.Log4j2ConfigFactory
import dk.sdu.cloud.service.k8.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.config.ConfigurationFactory
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
fun main(): Unit = runBlocking {
    ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
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

    return@runBlocking
}
