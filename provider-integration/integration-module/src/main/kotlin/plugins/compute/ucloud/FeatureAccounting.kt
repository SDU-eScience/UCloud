package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.reportDeltaUseCompute
import java.util.HashMap

object FeatureAccounting : JobFeature, Loggable {
    override val log = logger()

    override suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {
        account(rootJob.jobId, children + rootJob)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        val jobIds = jobBatch.asSequence().map { it.jobId }.toSet()
        for (jobId in jobIds) account(jobId, jobBatch)
    }

    private suspend fun JobManagement.account(jobId: String, batch: Collection<Container>) {
        val job = jobCache.findJob(jobId) ?: return
        if (job.status.state.isFinal()) return
        if (job.status.state == JobState.IN_QUEUE) return

        if (!reportDeltaUseCompute(job)) {
            val children = batch.filter { it.jobId == jobId }
            k8.addStatus(jobId, "Terminating job because of insufficient funds")
            children.forEach { it.cancel() }
        } else {
            k8.addStatus(jobId, "Charging job...")
        }
    }
}
