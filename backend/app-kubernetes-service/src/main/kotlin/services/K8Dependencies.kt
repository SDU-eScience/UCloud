package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.k8.KubernetesClient

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8Dependencies(
    val client: KubernetesClient,
    val scope: BackgroundScope,
    val serviceClient: AuthenticatedClient,
    val nameAllocator: NameAllocator,
    val dockerImageSizeQuery: DockerImageSizeQuery,
) {
    private val lastMessage = SimpleCache<String, String>(maxAge = 60_000 * 10, lookup = { null })

    suspend fun addStatus(jobId: String, message: String): Boolean {
        TODO()
        /*
        val last = lastMessage.get(jobId)
        if (last != message) {
            ComputationCallbackDescriptions.addStatus.call(
                AddStatusJob(jobId, message),
                serviceClient
            )
            lastMessage.insert(jobId, message)
            return true
        }
        return false
         */
    }

    suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String? = null
    ): Boolean {
        TODO()
        /*
        val last = lastMessage.get(jobId)
        val messageAsString = "${state}-${newStatus}"
        if (last != messageAsString) {
            ComputationCallbackDescriptions.requestStateChange.call(
                StateChangeRequest(jobId, state, newStatus),
                serviceClient
            ).orThrow()
            lastMessage.insert(jobId, messageAsString)
            return true
        }
        return false
         */
    }
}
