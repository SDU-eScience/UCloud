package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.AddStatusJob
import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.StateChangeRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.BackgroundScope
import io.fabric8.kubernetes.client.KubernetesClient

/**
 * A small dependencies bundle used by most of the K8 services.
 */
data class K8Dependencies(
    val client: KubernetesClient,
    val nameAllocator: K8NameAllocator,
    val scope: BackgroundScope,
    val serviceClient: AuthenticatedClient
) {
    suspend fun addStatus(jobId: String, message: String) {
        ComputationCallbackDescriptions.addStatus.call(
            AddStatusJob(jobId, message),
            serviceClient
        )
    }

    suspend fun changeState(
        jobId: String,
        state: JobState,
        newStatus: String? = null
    ) {
        ComputationCallbackDescriptions.requestStateChange.call(
            StateChangeRequest(jobId, state, newStatus),
            serviceClient
        ).orThrow()
    }
}
