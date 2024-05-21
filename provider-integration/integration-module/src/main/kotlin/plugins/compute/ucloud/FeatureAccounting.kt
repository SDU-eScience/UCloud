package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.reportJobUsage
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.cliffc.high_scale_lib.NonBlockingHashMapLong

object FeatureAccounting : JobFeature, Loggable {
    override val log = logger()

    private val lastUpdates = NonBlockingHashMapLong<Long>()

    override suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {
        account(rootJob.jobId, children + rootJob, force = true)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        val jobIds = jobBatch.asSequence().map { it.jobId }.toSet()
        for (jobId in jobIds) account(jobId, jobBatch, force = false)
    }

    private suspend fun JobManagement.account(jobId: String, batch: Collection<Container>, force: Boolean) {
        val job = jobCache.findJob(jobId) ?: return
        if (job.status.state.isFinal()) return
        if (job.status.state == JobState.IN_QUEUE) return
        val lastUpdatedAt = lastUpdates[jobId.toLong()] ?: 0L
        val now = Time.now()
        if (!force && now - lastUpdatedAt < 60_000) {
            return
        }

        lastUpdates[jobId.toLong()] = now

        if (!reportJobUsage(job)) {
            val children = batch.filter { it.jobId == jobId }
            k8.addStatus(jobId, "Terminating job because of insufficient funds")
            children.forEach { it.cancel() }
        }
    }
}
