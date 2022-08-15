package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.SimpleCache
import kotlinx.coroutines.CoroutineScope

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8Dependencies(
    var client: KubernetesClient,
    val scope: CoroutineScope,
    var serviceClient: AuthenticatedClient,
    val nameAllocator: NameAllocator,
    val debug: DebugSystem?,
    val jobCache: VerifiedJobCache,
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
        expectedDifferentState: Boolean = false,
        allowRestart: Boolean? = null,
    ): Boolean {
        val last = lastMessage.get(jobId)
        val messageAsString = "${state}-${newStatus}"
        val currentState = jobCache.findJob(jobId)?.status?.state ?: JobState.IN_QUEUE

        if (currentState != state && last != messageAsString) {
            JobsControl.update.call(
                bulkRequestOf(
                    ResourceUpdateAndId(
                        jobId,
                        JobUpdate(
                            state = state,
                            status = newStatus,
                            expectedState = expectedState,
                            expectedDifferentState = expectedDifferentState,
                            allowRestart = allowRestart,
                        )
                    )
                ),
                serviceClient
            )
            jobCache.setExpectedState(jobId, state)
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

    suspend fun updateMounts(jobId: String, mounts: List<String>) {
        JobsControl.update.call(
            bulkRequestOf(ResourceUpdateAndId(jobId, JobUpdate(newMounts = mounts))),
            serviceClient
        )
    }
}
