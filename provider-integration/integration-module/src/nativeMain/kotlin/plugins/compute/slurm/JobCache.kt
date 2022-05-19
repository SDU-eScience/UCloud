package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.service.SimpleCache

class JobCache(private val serviceClient: AuthenticatedClient) {
    private val cache = SimpleCache<String, Job>(
        maxAge = 1000 * 60 * 60,
        lookup = { id ->
            JobsControl.retrieve.call(
                ResourceRetrieveRequest(JobIncludeFlags(includeProduct = true), id),
                serviceClient
            ).orNull()
        }
    )

    suspend fun findJob(jobId: String): Job? {
        return cache.get(jobId)
    }

    suspend fun cacheJob(job: Job) {
        cache.insert(job.id, job)
    }
}
