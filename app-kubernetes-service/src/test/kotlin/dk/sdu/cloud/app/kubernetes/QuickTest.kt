package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.calls.types.StreamingFile
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

fun main() = runBlocking {
    val service = PodService(DefaultKubernetesClient())
    val requestId = "testing-${UUID.randomUUID()}"
    service.create(requestId)
    service.startContainer(requestId)
}
