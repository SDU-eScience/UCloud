package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.JobWithStatus
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import java.util.UUID
import java.util.Date

class JobHibernateDAO(
    private val applicationDAO: ApplicationHibernateDAO
) : JobDAO<HibernateSession> {
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
        applicationName: String,
        applicationVersion: String,
        jwt: String,
        numberOfNodes: Int
    ) {
        val appEntity = applicationDAO.internalByNameAndVersion(session, applicationName, applicationVersion)
                ?: throw JobBadApplication()

        session.save(
            JobEntity(
                systemId = UUID.fromString(systemId),
                owner = user,
                application = appEntity,
                createdAt = Date(),
                modifiedAt = Date(),
                state = AppState.VALIDATED,
                slurmId = null,
                status = null,
                sshUser = null,
                jobDirectory = null,
                workingDirectory = null,
                jwt = jwt,
                numberOfNodes = numberOfNodes
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
        status ?: "Unknown",
        application.id.name,
        application.id.version,
        createdAt.time,
        modifiedAt.time
    )

    private fun JobEntity.toJobInformation(): JobInformation = JobInformation(
        systemId.toString(),
        owner,
        application.id.name,
        application.id.version,
        slurmId,
        status,
        sshUser,
        jobDirectory,
        workingDirectory,
        createdAt.time,
        state,
        jwt,
        numberOfNodes
    )
}
