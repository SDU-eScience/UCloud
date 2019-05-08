package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.QueryInternalVncParametersRequest
import dk.sdu.cloud.app.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.api.QueryVncParametersResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class VncService<DBSession>(
    private val computationBackendService: ComputationBackendService,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun queryVncParameters(jobId: String, requestedBy: String): QueryInternalVncParametersResponse {
        val (job, _) = db.withTransaction { jobDao.find(it, jobId) }
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
