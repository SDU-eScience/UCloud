package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.JobStatus
import org.jetbrains.exposed.sql.*

data class JobToSlurm(val id: String, val slurmId: Long, val owner: String)
data class JobWithStatus(
        val jobId: String,
        val owner: String,
        val status: JobStatus
)

data class JobWithStatusAndInvocation(
        val jobInfo: JobWithStatus,
        val parameters: Map<String, Any>,
        val appName: String,
        val appVersion: String,
        val workingDirectory: String,
        val jobDirectory: String
)

// TODO Created/modified timestamps
object JobsTable : Table() {
    val systemId = varchar("system_id", 36).primaryKey()
    val slurmId = long("slurm_id")
    val owner = varchar("owner", 64)


    val appName = varchar("app_name", 64)
    val appVersion = varchar("app_version", 64)
    val parameters = varchar("json_parameters", 4096) // TODO Not a big fan of this
    val workingDirectory = varchar("working_directory", 1024)
    val jobDirectory = varchar("job_directory", 1024)
}

object JobStatusTable : Table() {
    val jobId = varchar("job_id", 36) references JobsTable.systemId
    val status = varchar("status", 32)
}

object JobsDAO {
    private val mapper = jacksonObjectMapper()

    private fun mapJobRow(row: ResultRow): JobToSlurm {
        return JobToSlurm(
                id = row[JobsTable.systemId],
                slurmId = row[JobsTable.slurmId],
                owner = row[JobsTable.owner]
        )
    }

    fun findJobMappingById(id: String): JobToSlurm? {
        return JobsTable.select { JobsTable.systemId eq id }.toList().map(::mapJobRow).singleOrNull()
    }

    fun findJobMappingBySlurmId(slurmId: String): JobToSlurm? {
        return JobsTable.select { JobsTable.slurmId eq slurmId }.toList().map(::mapJobRow).singleOrNull()
    }

    fun findSystemIdFromSlurmId(slurmId: Long): String? {
        return JobsTable
                .select { JobsTable.slurmId eq slurmId }
                .map { it[JobsTable.systemId] }.singleOrNull()
    }

    fun findAllJobsWithStatus(owner: String): List<JobWithStatus> {
        return (JobsTable innerJoin JobStatusTable).slice(
                JobsTable.systemId,
                JobsTable.owner,
                JobStatusTable.status
        ).select {
            JobsTable.owner eq owner
        }.toList().map {
            JobWithStatus(
                    jobId = it[JobsTable.systemId],
                    owner = it[JobsTable.owner],
                    status = JobStatus.valueOf(it[JobStatusTable.status])
            )
        }
    }

    private fun findJobWithStatusAndInvocationWhere(
            where: SqlExpressionBuilder.() -> Op<Boolean>
    ): JobWithStatusAndInvocation? {
        return (JobsTable innerJoin JobStatusTable).select(where).toList().map {
            JobWithStatusAndInvocation(
                    jobInfo = JobWithStatus(
                            jobId = it[JobsTable.systemId],
                            owner = it[JobsTable.owner],
                            status = JobStatus.valueOf(it[JobStatusTable.status])
                    ),
                    appName = it[JobsTable.appName],
                    appVersion = it[JobsTable.appVersion],
                    parameters = mapper.readValue(it[JobsTable.parameters]),
                    workingDirectory = it[JobsTable.workingDirectory],
                    jobDirectory = it[JobsTable.jobDirectory]
            )
        }.singleOrNull()
    }

    fun findJobWithStatusById(id: String): JobWithStatusAndInvocation? {
        return findJobWithStatusAndInvocationWhere {
            JobsTable.systemId eq id
        }
    }

    fun findJobWithStatusBySlurmId(slurmId: Long): JobWithStatusAndInvocation? {
        return findJobWithStatusAndInvocationWhere { JobsTable.slurmId eq slurmId }
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
            initialStatus: JobStatus = JobStatus.PENDING
    ) {
        JobsTable.insert {
            it[JobsTable.systemId] = systemId
            it[JobsTable.owner] = owner
            it[JobsTable.slurmId] = slurmId
            it[JobsTable.appName] = appName
            it[JobsTable.appVersion] = appVersion
            it[JobsTable.workingDirectory] = workingDirectory
            it[JobsTable.jobDirectory] = jobDirectory
            it[JobsTable.parameters] = mapper.writeValueAsString(parameters)
        }

        JobStatusTable.insert {
            it[JobStatusTable.jobId] = systemId
            it[JobStatusTable.status] = initialStatus.name
        }
    }

    fun updateJobBySystemId(systemId: String, newStatus: JobStatus): Boolean {
        val existing = findJobMappingById(systemId) ?: return false
        JobStatusTable.update({ JobStatusTable.jobId eq existing.id }, limit = 1) {
            it[JobStatusTable.status] = newStatus.name
        }
        return true
    }
}

object JobService {
    fun recentJobs(who: DecodedJWT): List<JobWithStatus> = JobsDAO.findAllJobsWithStatus(who.subject)
    fun findJob(id: String, who: DecodedJWT): JobWithStatusAndInvocation? =
            JobsDAO.findJobWithStatusById(id)?.takeIf { it.jobInfo.owner == who.subject }
}
