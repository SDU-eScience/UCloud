package dk.sdu.cloud.app.kubernetes.watcher.api

import dk.sdu.cloud.events.EventStreamContainer

data class JobCondition(val type: String?, val reason: String?)

data class JobEvent(
    val jobName: String,
    val condition: JobCondition?
)

object JobEvents : EventStreamContainer() {
    val events = stream<JobEvent>("app-kubernetes-job-events", { it.jobName })
}
