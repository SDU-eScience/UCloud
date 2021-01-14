package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlUpdateRequestItem
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.k8.KubernetesClient

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8Dependencies(
    var client: KubernetesClient,
    val scope: BackgroundScope,
    val serviceClient: AuthenticatedClient,
    val nameAllocator: NameAllocator,
    val dockerImageSizeQuery: DockerImageSizeQuery,
) {
    private val lastMessage = SimpleCache<String, String>(maxAge = 60_000 * 10, lookup = { null })

    suspend fun addStatus(jobId: String, message: String): Boolean {
        val last = lastMessage.get(jobId)
        if (last != message) {
            JobsControl.update.call(
                bulkRequestOf(JobsControlUpdateRequestItem(jobId, status = message)),
                serviceClient
            )
            lastMessage.insert(jobId, message)
            return true
        }
        return false
    }

    suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String? = null,
        expectedState: JobState? = null,
        expectedDifferentState: Boolean = false
    ): Boolean {
        val last = lastMessage.get(jobId)
        val messageAsString = "${state}-${newStatus}"
        if (last != messageAsString) {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        jobId,
                        state = state,
                        status = newStatus,
                        expectedState = expectedState,
                        expectedDifferentState = expectedDifferentState
                    )
                ),
                serviceClient
            )
            lastMessage.insert(jobId, messageAsString)
            return true
        }
        return false
    }
}
