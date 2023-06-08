package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.service.SimpleCache

class VerifiedJobCache(private val serviceClient: AuthenticatedClient) {
    private val cache = SimpleCache<String, Job>(
        maxAge = 1000 * 60 * 60 * 8,
        lookup = { id ->
            JobsControl.retrieve.call(
                ResourceRetrieveRequest(JobIncludeFlags(), id),
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

    suspend fun setExpectedState(jobId: String, newState: JobState) {
        cache.transformValue(jobId) { job ->
            job.copy(
                status = job.status.copy(
                    state = newState
                )
            )
        }
    }
}

