package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.Page
import dk.sdu.cloud.app.api.PaginationRequest
import org.jetbrains.exposed.sql.transactions.transaction

class JobService(
    private val dao: JobsDAO,
    private val jobExecutionService: JobExecutionService
) {
    fun recentJobs(who: DecodedJWT, paginationRequest: PaginationRequest): Page<JobWithStatus> =
        dao.transaction { findAllJobsWithStatus(who.subject, paginationRequest.normalize()) }

    fun findJobById(who: DecodedJWT, jobId: String): JobWithStatus? =
            dao.transaction { findJobById(who.subject, jobId) }

    fun startJob(who: DecodedJWT, req: AppRequest.Start): String {
        return jobExecutionService.startJob(req, who)
    }
}
