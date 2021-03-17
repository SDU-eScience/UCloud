package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@TSNamespace("compute.aau.jobs")
object AauCompute : JobsProvider("aau")

@Serializable
data class AauComputeSendUpdateRequest(
    val id: String,
    val update: String,
    val newState: JobState? = null,
)

@Serializable
data class AauComputeRetrieveRequest(val id: String)
typealias AauComputeRetrieveResponse = Job

@UCloudApiInternal(InternalLevel.BETA)
@TSNamespace("compute.aau.maintenance")
object AauComputeMaintenance : CallDescriptionContainer("jobs.compute.aau.maintenance") {
    val baseContext = "/ucloud/aau/compute/jobs/maintenance"

    val sendUpdate = call<BulkRequest<AauComputeSendUpdateRequest>, Unit, CommonErrorMessage>("sendUpdate") {
        httpUpdate(baseContext, "update", roles = Roles.PUBLIC) // Verified in code
    }

    val retrieve = call<AauComputeRetrieveRequest, AauComputeRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PUBLIC) // Verified in code
    }
}
