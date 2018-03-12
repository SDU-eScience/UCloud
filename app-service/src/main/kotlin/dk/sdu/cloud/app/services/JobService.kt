package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.JobStatus
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.JobWithStatusAndInvocation
import org.jetbrains.exposed.sql.transactions.transaction

class JobService(
    private val dao: JobsDAO
) {
    fun recentJobs(who: DecodedJWT): List<JobWithStatus> = transaction { dao.findAllJobsWithStatus(who.subject) }

    fun findJob(id: String, who: DecodedJWT): JobWithStatusAndInvocation? =
        transaction { dao.findJobWithStatusById(id)?.takeIf { it.jobInfo.owner == who.subject } }

    fun findJobWithStatusBySlurmId(slurmId: Long): JobWithStatusAndInvocation? {
        return transaction {
            dao.findJobWithStatusBySlurmId(slurmId)
        }
    }


    fun findSystemIdFromSlurmId(slurmId: Long): String? {
        return transaction {
            dao.findSystemIdFromSlurmId(slurmId)
        }
    }

    fun createJob(
        systemId: String,
        owner: String,
        slurmId: Long,
        appName: String,
        appVersion: String,
        workingDirectory: String,
        jobDirectory: String,
        parameters: Map<String, Any>,
        createdAt: Long,
        initialStatus: JobStatus = JobStatus.PENDING
    ) {
        transaction {
            dao.createJob(
                systemId,
                owner,
                slurmId,
                appName,
                appVersion,
                workingDirectory,
                jobDirectory,
                parameters,
                createdAt,
                initialStatus
            )
        }
    }

    fun updateJobBySystemId(systemId: String, newStatus: JobStatus, modifiedAt: Long): Boolean {
        return transaction { dao.updateJobBySystemId(systemId, newStatus, modifiedAt) }
    }
}