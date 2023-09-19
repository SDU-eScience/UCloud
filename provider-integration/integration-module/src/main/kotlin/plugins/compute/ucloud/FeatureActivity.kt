package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.loadedConfig
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.utils.ActivitySystem

object FeatureActivity : JobFeature {
    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        if (!loadedConfig.shouldRunServerCode()) return

        val observedOwners = HashSet<ResourceOwner>()
        for (job in jobBatch) {
            val resolvedJob = jobCache.findJob(job.jobId) ?: continue
            val state = resolvedJob.status.state
            if (state.isFinal() || state == JobState.SUSPENDED) continue
            if (resolvedJob.owner in observedOwners) continue
            observedOwners.add(resolvedJob.owner)
            ActivitySystem.trackUsageResourceOwner(resolvedJob.owner)
        }
    }
}
