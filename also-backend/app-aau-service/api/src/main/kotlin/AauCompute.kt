package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.orchestrator.api.Compute
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.calls.*

@TSNamespace("compute.aau.jobs")
object AauCompute : Compute("aau")

data class AauComputeSendUpdateRequest(
    val id: String,
    val update: String,
    val newState: JobState?,
)

data class AauComputeRetrieveRequest(val id: String)
typealias AauComputeRetrieveResponse = Job

@UCloudApiInternal(InternalLevel.BETA)
@TSNamespace("compute.aau.maintenance")
object AauComputeMaintenance : CallDescriptionContainer("jobs.compute.aau.maintenance") {
    val baseContext = "/ucloud/aau/compute/jobs/maintenance"

    val sendUpdate = call<BulkRequest<AauComputeSendUpdateRequest>, Unit, CommonErrorMessage>("sendUpdate") {
        httpUpdate(baseContext, "update", roles = Roles.PRIVILEGED)
    }

    val retrieve = call<AauComputeRetrieveRequest, AauComputeRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PRIVILEGED)
    }
}
