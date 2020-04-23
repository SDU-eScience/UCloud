package dk.sdu.cloud.app.kubernetes.watcher.services

import dk.sdu.cloud.app.kubernetes.watcher.api.JobCondition
import dk.sdu.cloud.app.kubernetes.watcher.api.JobEvent
import dk.sdu.cloud.app.kubernetes.watcher.api.JobEvents
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.batch.Job
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import kotlinx.coroutines.runBlocking

const val ROLE_LABEL = "role"

class JobWatcher(
    private val k8sClient: KubernetesClient,
    eventStreamService: EventStreamService,

    private val appRole: String = "sducloud-app",
    private val namespace: String = "app-kubernetes"
) {
    private val producer = eventStreamService.createProducer(JobEvents.events)

    fun startWatch() {
        ourJobs().list().items.forEach {
            handleJobEvent(it)
        }

        ourJobs().watch(object : Watcher<Job> {
            override fun onClose(cause: KubernetesClientException?) {
                // Do nothing
            }

            override fun eventReceived(action: Watcher.Action, resource: Job) {
                handleJobEvent(resource)
            }
        })
    }

    private fun ourJobs() = k8sClient.batch().jobs().inNamespace(namespace).withLabel(ROLE_LABEL, appRole)

    private fun handleJobEvent(job: Job): Unit = runBlocking {
        val jobName = job.metadata.name
        val condition = job.status?.conditions?.firstOrNull()?.let { cond ->
            JobCondition(cond.type, cond.reason)
        }

        log.info("Handling event: $jobName $condition")
        producer.produce(JobEvent(jobName, condition))
    }

    companion object : Loggable {
        override val log = logger()
    }
}
