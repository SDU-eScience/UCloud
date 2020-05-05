package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.NodeCondition
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.DefaultKubernetesClient

import kotlinx.coroutines.delay
import java.time.LocalDate

class KubernetesAlerting {

    private fun cleanSet(
        setToClean: MutableSet<String>,
        listOfLivePods: List<Pod>
    ): MutableSet<String> {
        val newSet = mutableSetOf<String>()

        setToClean.forEach { pod ->
            listOfLivePods.forEach {
                val podPrefix = it.metadata.name.substring(0, if (it.metadata.name.length <= 15) it.metadata.name.length else 15)
                if (pod == podPrefix) {
                    newSet.add(podPrefix)
                }
            }
        }

        return newSet
    }

    suspend fun crashLoopAndFailedDetection(alertService: AlertingService) {
        val client = DefaultKubernetesClient()

        var alreadyAlerted = mutableSetOf<String>()
        var date = LocalDate.now()

        while (true) {
            val listOfPods = client.pods().list().items
            listOfPods.forEach {
                //TODO Find a better way to make sure we dont get multiple alerts for failed jobs
                val podPrefix = it.metadata.name.substring(0, if (it.metadata.name.length <= 15) it.metadata.name.length else 15)
                when {
                    it.status.phase == "CrashLoopBackOff" && !alreadyAlerted.contains(podPrefix) -> {
                        val message = "ALERT: Pod: ${it.metadata.name} state is ${it.status.phase}"
                        alertService.createAlert(Alert(message))
                        alreadyAlerted.add(podPrefix)
                    }
                    it.status.phase == "Failed" && !alreadyAlerted.contains(podPrefix) -> {
                        val message = "ALERT: Pod: ${it.metadata.name} status is ${it.status.phase}"
                        alertService.createAlert(Alert(message))
                        alreadyAlerted.add(podPrefix)
                    }
                    else -> return@forEach
                }
            }

            //Clean map once a day
            if (date != LocalDate.now()) {
                alreadyAlerted = cleanSet(alreadyAlerted,listOfPods)
                date = LocalDate.now()
            }
            delay(FIFTEEN_SEC)
        }
    }

    suspend fun nodeStatus(alertService: AlertingService) {
        val client = DefaultKubernetesClient()
        val alerts = mutableListOf<String>()
        while (true) {
            val nodes = client.nodes().list().items
            nodes.forEach { node ->
                node.status.conditions.forEach {
                    when (it.type) {
                        "Ready" -> {
                            if (it.status == "Unknown") {
                                val alert = "${node.metadata.name} has unknown ready-state"
                                alerts.add(alert)
                            }
                            else if (!it.status!!.toBoolean()) {
                                val alert = "${node.metadata.name} is not ready"
                                alerts.add(alert)
                            }
                        }
                        "MemoryPressure" -> {
                            if (it.status!!.toBoolean()) {
                                val alert = "${node.metadata.name} has memory pressure. Node low on memory."
                                alerts.add(alert)
                            }
                        }
                        "PIDPressure" -> {
                            if (it.status!!.toBoolean()) {
                                val alert = "${node.metadata.name} has process pressure. To many processes on node."
                                alerts.add(alert)
                            }                        }
                        "DiskPressure" -> {
                            if (it.status!!.toBoolean()) {
                                val alert = "${node.metadata.name} has disk pressure. Node disk cap is low."
                                alerts.add(alert)
                            }                         }
                        "NetworkUnavailable" -> {
                            if (it.status!!.toBoolean()) {
                                val alert = "${node.metadata.name}'s network is not configured correctly."
                                alerts.add(alert)
                            }
                        }
                        else -> {
                            log.warn("Found type not specified: ${it.type}")
                        }
                    }
                }
            }
            if (alerts.isEmpty()) {
                log.info("All nodes are fine")
            }
            else {
                val allAlerts = alerts.joinToString("\n")
                alertService.createAlert(Alert(allAlerts))
            }
            alerts.clear()
            delay(FIVE_MIN)
        }
    }

    companion object : Loggable {
        override val log = ElasticAlerting.logger()
    }
}
