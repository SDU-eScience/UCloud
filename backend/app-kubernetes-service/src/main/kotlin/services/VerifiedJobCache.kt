package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull

class VerifiedJobCache(private val serviceClient: AuthenticatedClient) {
    private val jobIdToJob = HashMap<String, Job>()

    suspend fun findJob(id: String): Job? {
        TODO()
        /*
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
         */
    }

    fun cacheJob(job: Job) {
        jobIdToJob[job.id] = job
    }
}
