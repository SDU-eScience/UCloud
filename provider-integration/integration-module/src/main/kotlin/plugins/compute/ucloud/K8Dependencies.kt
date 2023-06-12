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

interface K8Dependencies {
    val scope: CoroutineScope
    var serviceClient: AuthenticatedClient
    val debug: DebugSystem
    val jobCache: VerifiedJobCache

    suspend fun addStatus(jobId: String, vararg message: String, forceSend: Boolean = false): Boolean

    suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String? = null,
        expectedState: JobState? = null,
        expectedDifferentState: Boolean = false,
        allowRestart: Boolean? = null,
    ): Boolean

    suspend fun updateTimeAllocation(jobId: String, newAllocation: Long)

    suspend fun updateMounts(jobId: String, mounts: List<String>)
}

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8DependenciesImpl(
    var client: KubernetesClient,
    override val scope: CoroutineScope,
    override var serviceClient: AuthenticatedClient,
    val nameAllocator: NameAllocator,
    override val debug: DebugSystem,
    override val jobCache: VerifiedJobCache,
) : K8Dependencies {
    private val lastMessage = SimpleCache<String, String>(maxAge = 60_000 * 10, lookup = { null })

    override suspend fun addStatus(jobId: String, vararg message: String, forceSend: Boolean): Boolean {
        if (message.isEmpty()) return false
        val last = lastMessage.get(jobId)
        if (message.size > 1 || last != message.singleOrNull() || forceSend) {
            JobsControl.update.call(
                bulkRequestOf(
                    message.map { ResourceUpdateAndId(jobId, JobUpdate(status = it)) }
                ),
                serviceClient
            )
            lastMessage.insert(jobId, message.last())
            return true
        }
        return false
    }

    override suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String?,
        expectedState: JobState?,
        expectedDifferentState: Boolean,
        allowRestart: Boolean?,
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

    override suspend fun updateTimeAllocation(jobId: String, newAllocation: Long) {
        JobsControl.update.call(
            bulkRequestOf(ResourceUpdateAndId(jobId, JobUpdate(newTimeAllocation = newAllocation))),
            serviceClient
        )
    }

    override suspend fun updateMounts(jobId: String, mounts: List<String>) {
        JobsControl.update.call(
            bulkRequestOf(ResourceUpdateAndId(jobId, JobUpdate(newMounts = mounts))),
            serviceClient
        )
    }
}
