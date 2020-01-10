package dk.sdu.cloud.logchecker.services

import io.fabric8.kubernetes.client.DefaultKubernetesClient

class KubernetesLogChecker {

    fun checkLog() {

    }

    fun getLogs() {

    }

    fun restartLogger() {

    }

    fun runService() {
        val client = DefaultKubernetesClient()
        val listOfLogPods = client.pods().inNamespace("cattle-logging").list().items
        listOfLogPods.forEach { pod ->
            client.pods().inNamespace("cattle-logging").withName(pod.metadata.name).tailingLines(1000)

        }
    }

}
