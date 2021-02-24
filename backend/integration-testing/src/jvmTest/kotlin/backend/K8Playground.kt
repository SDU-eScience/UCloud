package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.integration.K3sContainer
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.service.k8.*
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

class K8Playground {
    @Test
    fun `just do something`() {
        val tempDir = File("/tmp")
        val k3sContainer = K3sContainer()
        k3sContainer.start()
        while (true) {
            if (k3sContainer.execInContainer("kubectl", "get", "node").exitCode == 0) break
            Thread.sleep(100)
        }
        while (true) {
            if (k3sContainer.execInContainer("stat", "/etc/rancher/k3s/k3s.yaml").exitCode == 0) break
            Thread.sleep(100)
        }
        val target = File(tempDir, "k3s.yml") // .also { it.deleteOnExit() }
        k3sContainer.copyFileFromContainer("/etc/rancher/k3s/k3s.yaml", target.absolutePath)
        val correctConfig = target.readText().replace("127.0.0.1:6443", "127.0.0.1:${k3sContainer.getMappedPort(6443)}")
        target.writeText(correctConfig)

        val client = KubernetesClient(KubernetesConfigurationSource.KubeConfigFile(target.absolutePath, null))

        runBlocking {
            println(client.listResources<Namespace>(KubernetesResources.namespaces).items.map { it.metadata?.name })
        }
    }
}