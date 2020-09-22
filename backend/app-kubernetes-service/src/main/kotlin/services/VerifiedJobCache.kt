package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull

class VerifiedJobCache(private val serviceClient: AuthenticatedClient) {
    private val jobIdToJob = HashMap<String, VerifiedJob>()

    suspend fun findJob(id: String): VerifiedJob? {
        return jobIdToJob[id] ?: run {
            val jobByUrl = ComputationCallbackDescriptions.lookupUrl.call(
                FindByStringId(id),
                serviceClient
            ).orNull()
            if (jobByUrl != null) {
                cacheJob(jobByUrl)
                jobByUrl
            } else {
                val jobById = ComputationCallbackDescriptions.lookup.call(
                    FindByStringId(id),
                    serviceClient
                ).orNull()

                if (jobById != null) cacheJob(jobById)
                jobById
            }
        }
    }

    fun cacheJob(job: VerifiedJob) {
        jobIdToJob[job.id] = job
    }
}
