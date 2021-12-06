package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.k8.KubernetesClient

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8Dependencies(
    var client: KubernetesClient,
    val scope: BackgroundScope,
    var serviceClient: AuthenticatedClient,
    val nameAllocator: NameAllocator,
    val dockerImageSizeQuery: DockerImageSizeQuery,
    val debug: DebugSystem?,
) {
    private val lastMessage = SimpleCache<String, String>(maxAge = 60_000 * 10, lookup = { null })

    suspend fun addStatus(jobId: String, message: String): Boolean {
        val last = lastMessage.get(jobId)
        if (last != message) {
            JobsControl.update.call(
                bulkRequestOf(ResourceUpdateAndId(jobId, JobUpdate(status = message))),
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
                    ResourceUpdateAndId(
                        jobId,
                        JobUpdate(
                            state = state,
                            status = newStatus,
                            expectedState = expectedState,
                            expectedDifferentState = expectedDifferentState,
                        )
                    )
                ),
                serviceClient
            )
            lastMessage.insert(jobId, messageAsString)
            return true
        }
        return false
    }

    suspend fun updateTimeAllocation(jobId: String, newAllocation: Long) {
        JobsControl.update.call(
            bulkRequestOf(ResourceUpdateAndId(jobId, JobUpdate(newTimeAllocation = newAllocation))),
            serviceClient
        )
    }
}
