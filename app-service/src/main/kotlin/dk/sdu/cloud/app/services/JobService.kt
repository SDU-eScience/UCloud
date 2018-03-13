package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.JobWithStatus
import org.jetbrains.exposed.sql.transactions.transaction

class JobService(
    private val dao: JobsDAO,
    private val jobExecutionService: JobExecutionService
) {
    // Queries
    fun recentJobs(who: DecodedJWT): List<JobWithStatus> = transaction { dao.findAllJobsWithStatus(who.subject) }

    fun startJob(req: AppRequest.Start, principal: DecodedJWT): String {
        return jobExecutionService.startJob(req, principal)
    }
}
