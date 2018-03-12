package dk.sdu.cloud.app.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.JobStatus
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.app.api.JobWithStatusAndInvocation
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime

data class JobToSlurm(val id: String, val slurmId: Long, val owner: String)

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

    val modifiedAt = datetime("modified_at")
    val createdAt = datetime("created_at")
}

class JobsDAO {
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
            JobsTable.appName,
            JobsTable.appVersion,
            JobStatusTable.status,
            JobStatusTable.modifiedAt,
            JobStatusTable.createdAt
        ).select {
            JobsTable.owner eq owner
        }.orderBy(JobStatusTable.modifiedAt, false)
            .toList()
            .map {
                JobWithStatus(
                    jobId = it[JobsTable.systemId],
                    owner = it[JobsTable.owner],
                    status = JobStatus.valueOf(it[JobStatusTable.status]),
                    appName = it[JobsTable.appName],
                    appVersion = it[JobsTable.appVersion],
                    createdAt = it[JobStatusTable.createdAt].millis,
                    modifiedAt = it[JobStatusTable.modifiedAt].millis
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
                    status = JobStatus.valueOf(it[JobStatusTable.status]),
                    appName = it[JobsTable.appName],
                    appVersion = it[JobsTable.appVersion],
                    createdAt = it[JobStatusTable.createdAt].millis,
                    modifiedAt = it[JobStatusTable.modifiedAt].millis
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
        createdAt: Long,
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
            it[JobStatusTable.createdAt] = DateTime(createdAt)
            it[JobStatusTable.modifiedAt] = DateTime(createdAt)
        }
    }

    fun updateJobBySystemId(systemId: String, newStatus: JobStatus, modifiedAt: Long): Boolean {
        val existing = findJobMappingById(systemId) ?: return false
        JobStatusTable.update({ JobStatusTable.jobId eq existing.id }, limit = 1) {
            it[JobStatusTable.status] = newStatus.name
            it[JobStatusTable.modifiedAt] = DateTime(modifiedAt)
        }
        return true
    }
}

