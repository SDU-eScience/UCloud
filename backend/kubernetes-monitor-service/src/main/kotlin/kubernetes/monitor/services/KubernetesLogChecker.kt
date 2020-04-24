package dk.sdu.cloud.kubernetes.monitor.services

import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.system.exitProcess

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
            try {
                val listOfLogPods = client.inNamespace(namespace).pods().list().items.filter { pod ->
                    pod.metadata.name.contains("logging-fluentd-linux")
                }
                listOfLogPods.forEach { pod ->
                    val podName = pod.metadata.name
                    val containerName = podName.substringBeforeLast("-").substringBeforeLast("-")
                    val lines =
                        client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                            .tailingLines(100)
                            .log.split("\n")
                    for (line in lines) {
                        if (line.contains("retry_time=10")) {
                            restartPod(podName)
                            break
                        }
                    }
                }
                if (currentTime == nextReportTime) {
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
                } else {
                    currentTime = LocalDate.now()
                }
                Thread.sleep(15 * 60 * 1000L)
            } catch (ex: Exception) {
                //if exception exit with error and restart pod
                log.info("Exception caught:")
                ex.printStackTrace()
                exitProcess(1)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
