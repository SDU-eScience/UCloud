package dk.sdu.cloud.kubernetesUtils.services

import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class KubernetesLogChecker(val alertingService: AlertingService) {
    private val client: DefaultKubernetesClient = DefaultKubernetesClient()
    private val namespace = "cattle-logging"
    private var numberOfRestarts = 0

    private fun restartPod(pod: String) {
        log.info("restarting: $pod")
        client.pods().inNamespace(namespace).withName(pod).delete()
        numberOfRestarts++
    }

    fun checkAndRestartFluentdLoggers() {
        var currentTime = LocalDate.now()
        var nextReportTime = LocalDate.now().plusDays(1)
        while (true) {
            val listOfLogPods = client.inNamespace(namespace).pods().list().items.filter {
                    pod -> pod.metadata.name.contains("logging-fluentd-linux")
            }
            listOfLogPods.forEach outer@{ pod ->
                val podName = pod.metadata.name
                val containerName = podName.substringBeforeLast("-").substringBeforeLast("-")
                val lines =
                    client.pods().inNamespace(namespace).withName(podName).inContainer(containerName).tailingLines(100)
                        .log.split("\n")
                lines.forEach { line ->
                    if (line.contains("retry_time=10")) {
                        restartPod(podName)
                        return@outer
                    }
                }
            }
            if ( currentTime == nextReportTime) {
                if (numberOfRestarts > 0) {
                    runBlocking {
                        alertingService.createAlert(
                            Alert("Number of restarts for $currentTime was: $numberOfRestarts")
                        )
                    }
                    numberOfRestarts = 0
                }
                currentTime = LocalDate.now()
                nextReportTime = LocalDate.now().plusDays(1)
            }
            else {
                currentTime = LocalDate.now()
            }
            Thread.sleep(15 * 60 * 1000L)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
