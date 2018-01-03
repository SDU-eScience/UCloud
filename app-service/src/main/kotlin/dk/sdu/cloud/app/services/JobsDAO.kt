package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.JobStatus
import org.jetbrains.exposed.sql.*

data class JobToSlurm(val id: String, val slurmId: String, val owner: String)
data class JobWithStatus(val jobId: String, val owner: String, val status: JobStatus)

// TODO Created/modified timestamps
object JobsTable : Table() {
    val id = varchar("id", 36).primaryKey()
    val slurmId = varchar("slurm_id", 36)
    val owner = varchar("owner", 64)
}

object JobStatusTable : Table() {
    val jobId = varchar("job_id", 36) references JobsTable.id
    val status = varchar("status", 32)
}

object JobsDAO {
    private fun mapJobRow(row: ResultRow): JobToSlurm {
        return JobToSlurm(
                id = row[JobsTable.id],
                slurmId = row[JobsTable.slurmId],
                owner = row[JobsTable.owner]
        )
    }

    fun findJobMappingById(id: String): JobToSlurm? {
        return JobsTable.select { JobsTable.id eq id }.toList().map(::mapJobRow).singleOrNull()
    }

    fun findJobMappingBySlurmId(slurmId: String): JobToSlurm? {
        return JobsTable.select { JobsTable.slurmId eq slurmId }.toList().map(::mapJobRow).singleOrNull()
    }

    fun findAllJobsWithStatus(owner: String): List<JobWithStatus> {
        return (JobsTable innerJoin JobStatusTable).slice(
                JobsTable.id,
                JobsTable.owner,
                JobStatusTable.status
        ).select {
            JobsTable.owner eq owner
        }.toList().map {
            JobWithStatus(
                    jobId = it[JobsTable.id],
                    owner = it[JobsTable.owner],
                    status = JobStatus.valueOf(it[JobStatusTable.status])
            )
        }
    }

    fun findJobWithStatusById(id: String): JobWithStatus? {
        return (JobsTable innerJoin JobStatusTable).slice(
                JobsTable.id,
                JobsTable.owner,
                JobStatusTable.status
        ).select {
            JobsTable.id eq id
        }.toList().map {
            JobWithStatus(
                    jobId = it[JobsTable.id],
                    owner = it[JobsTable.owner],
                    status = JobStatus.valueOf(it[JobStatusTable.status])
            )
        }.singleOrNull()
    }

    fun createJob(job: JobToSlurm, initialStatus: JobStatus = JobStatus.PENDING) {
        JobsTable.insert {
            it[JobsTable.id] = job.id
            it[JobsTable.owner] = job.owner
            it[JobsTable.slurmId] = job.slurmId
        }

        JobStatusTable.insert {
            it[JobStatusTable.jobId] = job.id
            it[JobStatusTable.status] = initialStatus.name
        }
    }

    fun updateJobBySlurmId(slurmId: String, newStatus: JobStatus): Boolean {
        val existing = findJobMappingBySlurmId(slurmId) ?: return false
        JobStatusTable.update({ JobStatusTable.jobId eq existing.id }, limit = 1) {
            it[JobStatusTable.status] = newStatus.name
        }
        return true
    }
}

object JobService {
    fun recentJobs(who: DecodedJWT): List<JobWithStatus> = JobsDAO.findAllJobsWithStatus(who.subject)
    fun findJob(id: String, who: DecodedJWT): JobWithStatus? =
            JobsDAO.findJobWithStatusById(id)?.takeIf { it.owner == who.subject }
}

