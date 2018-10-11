package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.JobWithStatus
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
    val state: AppState,
    val jwt: String
)

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

        applicationName: String,
        applicationVersion: String,

        jwt: String
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
