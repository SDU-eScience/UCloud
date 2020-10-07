package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.service.k8.KubernetesClient
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.exec
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = KubernetesClient()
    client.exec(
        KubernetesResources.pod.withNameAndNamespace("test-64c67d47dc-h2ptr", "app-kubernetes"),
        listOf("sh")
    ) {
        launch {
            outputs.consumeEach { f ->
                print(f.bytes.toString(Charsets.UTF_8))
            }
        }
        stdin.send("echo \"Hello world\"\n".toByteArray(Charsets.UTF_8))
        stdin.send("exit 0\n".toByteArray(Charsets.UTF_8))
    }
    return@runBlocking
}