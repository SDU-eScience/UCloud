package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime

data class JobInformation(
    val systemId: String,
    val owner: String,
    val appName: String,
    val appVersion: String,
    val slurmId: Long?,
    val status: String?,
    val sshUser: String?,
    val jobDirectory: String?,
    val workingDirectory: String?,
    val createdAt: Long
)

object JobsTable : Table() {
    val systemId = varchar("system_id", 36).primaryKey()
    val owner = text("owner")
    val appName = text("app_name")
    val appVersion = text("app_version")
    val createdAt = datetime("created_at")

    val slurmId = long("slurm_id").nullable()
    val status = text("status").nullable()

    val sshUser = text("ssh_user").nullable()
    val jobDirectory = text("job_directory").nullable()
    val workingDirectory = text("working_directory").nullable()
}

object JobStatusTable : Table() {
    val jobId = varchar("job_id", 36) references JobsTable.systemId
    val status = varchar("state", 32)
    val createdAt = datetime("created_at")
}

class JobsDAO {
    fun <T> transaction(body: JobsDAO.() -> T): T {
        return org.jetbrains.exposed.sql.transactions.transaction { body() }
    }

    fun findJobInformationBySlurmId(slurmId: Long): JobInformation? {
        return JobsTable.select { JobsTable.slurmId eq slurmId }.toList().map {
            JobInformation(
                it[JobsTable.systemId],
                it[JobsTable.owner],
                it[JobsTable.appName],
                it[JobsTable.appVersion],
                it[JobsTable.slurmId],
                it[JobsTable.status],
                it[JobsTable.sshUser],
                it[JobsTable.jobDirectory],
                it[JobsTable.workingDirectory],
                it[JobsTable.createdAt].millis
            )
        }.singleOrNull()
    }

    // TODO DB has changed
    fun findAllJobsWithStatus(owner: String): List<JobWithStatus> {
        return (JobsTable innerJoin JobStatusTable).slice(
            JobsTable.systemId,
            JobsTable.owner,
            JobsTable.appName,
            JobsTable.appVersion,
            JobStatusTable.status,
            JobStatusTable.createdAt
        ).select {
            JobsTable.owner eq owner
        }.orderBy(JobStatusTable.createdAt, false)
            .toList()
            .map {
                JobWithStatus(
                    jobId = it[JobsTable.systemId],
                    owner = it[JobsTable.owner],
                    status = AppState.valueOf(it[JobStatusTable.status]),
                    appName = it[JobsTable.appName],
                    appVersion = it[JobsTable.appVersion],
                    createdAt = it[JobsTable.createdAt].millis,
                    modifiedAt = it[JobStatusTable.createdAt].millis
                )
            } // TODO This should probably be done in the query
            .groupBy { it.jobId }
            .values
            .map { it.first() }
    }

    fun createJob(
        systemId: String,
        owner: String,
        appDescription: ApplicationDescription
    ) {
        JobsTable.insert {
            it[JobsTable.systemId] = systemId
            it[JobsTable.owner] = owner
            it[JobsTable.appName] = appDescription.info.name
            it[JobsTable.appVersion] = appDescription.info.version
            it[JobsTable.createdAt] = DateTime.now()
        }

        JobStatusTable.insert {
            it[JobStatusTable.jobId] = systemId
            it[JobStatusTable.status] = AppState.VALIDATED.toString()
            it[JobStatusTable.createdAt] = DateTime.now()
        }
    }

    fun updateJobWithSlurmInformation(
        systemId: String,
        sshUser: String,
        jobDirectory: String,
        workingDirectory: String,
        slurmId: Long
    ) {
        JobsTable.update({ JobsTable.systemId eq systemId }, limit = 1) {
            it[JobsTable.sshUser] = sshUser
            it[JobsTable.jobDirectory] = jobDirectory
            it[JobsTable.workingDirectory] = workingDirectory
            it[JobsTable.slurmId] = slurmId
        }
    }

    fun updateJobBySystemId(systemId: String, newState: AppState, message: String? = null) {
        JobStatusTable.insert {
            it[JobStatusTable.jobId] = systemId
            it[JobStatusTable.status] = newState.toString()
            it[JobStatusTable.createdAt] = DateTime.now()
        }

        if (message != null) {
            JobsTable.update({ JobStatusTable.jobId eq systemId }, limit = 1) {
                it[JobsTable.status] = message
            }
        }
    }
}

