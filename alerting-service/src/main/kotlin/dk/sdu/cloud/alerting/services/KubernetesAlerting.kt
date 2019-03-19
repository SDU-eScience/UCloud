package dk.sdu.cloud.alerting.services

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

}
