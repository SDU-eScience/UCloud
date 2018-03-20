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
    val createdAt: Long,
    val state: AppState
)

object JobsTable : Table() {
    val systemId = varchar("system_id", 36).primaryKey()
    val owner = varchar("owner", 128).index()
    val appName = text("app_name")
    val appVersion = text("app_version")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val state = text("state")

    val slurmId = long("slurm_id").nullable()
    val status = text("status").nullable()

    val sshUser = text("ssh_user").nullable()
    val jobDirectory = text("job_directory").nullable()
    val workingDirectory = text("working_directory").nullable()
}

class JobsDAO {
    fun <T> transaction(body: JobsDAO.() -> T): T {
        return org.jetbrains.exposed.sql.transactions.transaction { body() }
    }

    fun findJobInformationBySlurmId(slurmId: Long): JobInformation? {
        return JobsTable
            .select { JobsTable.slurmId eq slurmId }
            .toList()
            .map(this::mapJobsTableRowToJobInformation)
            .singleOrNull()
    }

    fun findJobInformationByJobId(owner: String, jobId: String): JobInformation? {
        return JobsTable
            .select { (JobsTable.systemId eq jobId) and (JobsTable.owner eq owner) }
            .toList()
            .map(this::mapJobsTableRowToJobInformation)
            .singleOrNull()
    }

    private fun mapJobsTableRowToJobInformation(it: ResultRow): JobInformation =
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
            it[JobsTable.createdAt].millis,
            AppState.valueOf(it[JobsTable.state])
        )

    fun findAllJobsWithStatus(
        owner: String,
        paginationRequest: NormalizedPaginationRequest
    ): Page<JobWithStatus> {
        val jobsOwnedByUser = JobsTable.select { JobsTable.owner eq owner }.count()

        val rawItems =
            if (jobsOwnedByUser == 0) emptyList()
            else JobsTable.slice(
                JobsTable.systemId,
                JobsTable.owner,
                JobsTable.appName,
                JobsTable.appVersion,
                JobsTable.createdAt,
                JobsTable.modifiedAt,
                JobsTable.state,
                JobsTable.status
            ).select { JobsTable.owner eq owner }
                .orderBy(JobsTable.modifiedAt, false)
                .limit(
                    n = paginationRequest.itemsPerPage,
                    offset = paginationRequest.itemsPerPage * paginationRequest.page
                )
                .toList()
                .map {
                    JobWithStatus(
                        jobId = it[JobsTable.systemId],
                        owner = it[JobsTable.owner],
                        state = AppState.valueOf(it[JobsTable.state]),
                        status = it[JobsTable.status] ?: "",
                        appName = it[JobsTable.appName],
                        appVersion = it[JobsTable.appVersion],
                        createdAt = it[JobsTable.createdAt].millis,
                        modifiedAt = it[JobsTable.modifiedAt].millis
                    )
                }
        return Page(jobsOwnedByUser, paginationRequest.itemsPerPage, paginationRequest.page, rawItems)
    }

    fun findJobById(owner: String, jobId: String): JobWithStatus? {
        return JobsTable.slice(
            JobsTable.systemId,
            JobsTable.owner,
            JobsTable.appName,
            JobsTable.appVersion,
            JobsTable.createdAt,
            JobsTable.modifiedAt,
            JobsTable.state,
            JobsTable.status
        )
            .select {
                (JobsTable.owner eq owner) and
                        (JobsTable.systemId eq jobId)
            }
            .toList()
            .map {
                JobWithStatus(
                    jobId = it[JobsTable.systemId],
                    owner = it[JobsTable.owner],
                    state = AppState.valueOf(it[JobsTable.state]),
                    status = it[JobsTable.status] ?: "",
                    appName = it[JobsTable.appName],
                    appVersion = it[JobsTable.appVersion],
                    createdAt = it[JobsTable.createdAt].millis,
                    modifiedAt = it[JobsTable.modifiedAt].millis
                )
            }
            .singleOrNull()
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
            it[JobsTable.modifiedAt] = DateTime.now()
            it[JobsTable.state] = AppState.VALIDATED.toString()
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
            it[JobsTable.createdAt] = DateTime.now()
        }
    }

    fun updateJobBySystemId(systemId: String, newState: AppState, message: String? = null) {
        JobsTable.update({ JobsTable.systemId eq systemId }, limit = 1) {
            if (message != null) it[JobsTable.status] = message
            it[JobsTable.state] = newState.toString()
            it[JobsTable.modifiedAt] = DateTime.now()
        }
    }
}

