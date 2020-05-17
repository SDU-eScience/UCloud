package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.QueryInternalVncParametersRequest
import dk.sdu.cloud.app.orchestrator.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.orchestrator.api.QueryVncParametersResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class VncService(
    private val computationBackendService: ComputationBackendService,
    private val db: DBContext,
    private val jobs: JobQueryService,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun queryVncParameters(jobId: String, requestedBy: String): QueryInternalVncParametersResponse {
        val (job) = jobs.find(db, listOf(jobId), requestedBy).first()
        if (job.owner != requestedBy) throw RPCException("Not found", HttpStatusCode.NotFound)

        val backend = computationBackendService.getAndVerifyByName(job.backend)
        return backend.queryInternalVncParameters.call(
            QueryInternalVncParametersRequest(job),
            serviceClient
        ).orThrow()
    }
}

fun QueryInternalVncParametersResponse.exportForEndUser(): QueryVncParametersResponse =
    QueryVncParametersResponse(path, password)
