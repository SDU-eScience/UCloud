package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "jobs")
data class JobEntity(
    @Id
    @NaturalId
    var systemId: UUID,

    var owner: String,

    var appName: String,

    var appVersion: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Enumerated(EnumType.STRING)
    var state: AppState,

    var slurmId: Long?,

    var status: String?,

    var sshUser: String?,

    var jobDirectory: String?,

    var workingDirectory: String?
) {
    companion object : HibernateEntity<JobEntity>, WithId<UUID>
}

class JobHibernateDAO : JobDAO<HibernateSession> {
    override fun findJobInformationBySlurmId(session: HibernateSession, slurmId: Long): JobInformation? {
        return session.criteria<JobEntity> { entity[JobEntity::slurmId] equal slurmId }.uniqueResult()
            ?.toJobInformation()
    }

    override fun findJobInformationByJobId(session: HibernateSession, user: String, jobId: String): JobInformation? {
        return session.criteria<JobEntity> {
            (entity[JobEntity::systemId] equal UUID.fromString(jobId)) and (entity[JobEntity::owner] equal user)
        }.uniqueResult()?.toJobInformation()
    }

    override fun findAllJobsWithStatus(
        session: HibernateSession,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<JobWithStatus> {
        return session
            .paginatedCriteria<JobEntity>(paging) { entity[JobEntity::owner] equal user }
            .mapItems { it.toJobWithStatus() }
    }

    override fun findJobById(session: HibernateSession, user: String, jobId: String): JobWithStatus? {
        return session.criteria<JobEntity> {
            (entity[JobEntity::owner] equal user) and (entity[JobEntity::systemId] equal UUID.fromString(jobId))
        }.uniqueResult()?.toJobWithStatus()
    }

    override fun createJob(
        session: HibernateSession,
        user: String,
        systemId: String,
        appDescription: NormalizedApplicationDescription
    ) {
        session.save(
            JobEntity(
                systemId = UUID.fromString(systemId),
                owner = user,
                appName = appDescription.info.name,
                appVersion = appDescription.info.version,
                createdAt = Date(),
                modifiedAt = Date(),
                state = AppState.VALIDATED,
                slurmId = null,
                status = null,
                sshUser = null,
                jobDirectory = null,
                workingDirectory = null
            )
        )
    }

    override fun updateJobWithSlurmInformation(
        session: HibernateSession,
        systemId: String,
        sshUser: String,
        jobDirectory: String,
        workingDirectory: String,
        slurmId: Long
    ) {
        val entity = JobEntity[session, UUID.fromString(systemId)] ?: throw JobNotFoundException(systemId)
        entity.sshUser = sshUser
        entity.jobDirectory = jobDirectory
        entity.workingDirectory = workingDirectory
        entity.slurmId = slurmId
        entity.modifiedAt = Date()
        session.update(entity)
    }

    override fun updateJobBySystemId(
        session: HibernateSession,
        systemId: String,
        newState: AppState,
        message: String?
    ) {
        val entity = JobEntity[session, UUID.fromString(systemId)] ?: throw JobNotFoundException(systemId)
        entity.status = message
        entity.state = newState
        entity.modifiedAt = Date()
        session.update(entity)
    }

    private fun JobEntity.toJobWithStatus(): JobWithStatus = JobWithStatus(
        systemId.toString(),
        owner,
        state,
        status!!,
        appName,
        appVersion,
        createdAt.time,
        modifiedAt.time
    )

    private fun JobEntity.toJobInformation(): JobInformation = JobInformation(
        systemId.toString(),
        owner,
        appName,
        appVersion,
        slurmId,
        status,
        sshUser,
        jobDirectory,
        workingDirectory,
        createdAt.time,
        state
    )
}