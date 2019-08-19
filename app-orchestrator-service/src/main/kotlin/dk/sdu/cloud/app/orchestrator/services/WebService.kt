package dk.sdu.cloud.app.orchestrator.services
import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersRequest
import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersResponse
import dk.sdu.cloud.app.orchestrator.api.QueryWebParametersResponse
import dk.sdu.cloud.app.orchestrator.services.JobDao
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class WebService<DBSession>(
    private val computationBackendService: ComputationBackendService,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun queryWebParameters(jobId: String, requestedBy: String): QueryInternalWebParametersResponse {
        val (job) = db.withTransaction { jobDao.find(it, jobId) }
        if (job.owner != requestedBy) throw RPCException("Not found", HttpStatusCode.NotFound)

        val backend = computationBackendService.getAndVerifyByName(job.backend)
        return backend.queryInternalWebParameters.call(
            QueryInternalWebParametersRequest(job),
            serviceClient
        ).orThrow()
    }
}

fun QueryInternalWebParametersResponse.exportForEndUser(): QueryWebParametersResponse =
    QueryWebParametersResponse(path)
