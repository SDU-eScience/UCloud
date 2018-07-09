package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

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

/*
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
*/

interface JobDAO<Session> {
    fun findJobInformationBySlurmId(
        session: Session,
        slurmId: Long
    ): JobInformation?

    fun findJobInformationByJobId(
        session: Session,
        user: String,
        jobId: String
    ): JobInformation?

    fun findAllJobsWithStatus(
        session: Session,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<JobWithStatus>

    fun findJobById(
        session: Session,
        user: String,
        jobId: String
    ): JobWithStatus?

    fun createJob(
        session: Session,
        user: String,
        systemId: String,
        appDescription: NormalizedApplicationDescription
    )

    fun updateJobWithSlurmInformation(
        session: Session,
        systemId: String,
        sshUser: String,
        jobDirectory: String,
        workingDirectory: String,
        slurmId: Long
    )

    fun updateJobBySystemId(
        session: Session,
        systemId: String,
        newState: AppState,
        message: String? = null
    )
}
