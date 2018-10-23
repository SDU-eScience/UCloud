package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.service.db.*
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "job_information")
data class JobInformationEntity(
    @Id
    @NaturalId
    val systemId: String,

    var owner: String,

    @Embedded
    var application: EmbeddedNameAndVersion,

    var status: String,

    var state: JobState,

    var nodes: Int,

    var tasksPerNode: Int,

    @Type(type = JSONB_TYPE)
    var parameters: Map<String, Any?>,

    @Type(type = JSONB_TYPE)
    var files: List<ValidatedFileForUpload>,

    var maxTimeHours: Int,

    var maxTimeMinutes: Int,

    var maxTimeSeconds: Int,

    var backendName: String,

    var accessToken: String,

    override var createdAt: Date,

    override var modifiedAt: Date
) : WithTimestamps {
    companion object : HibernateEntity<JobInformationEntity>, WithId<String>
}

class JobHibernateDao(
    private val appDao: ApplicationDAO<HibernateSession>
) : JobDao<HibernateSession> {
    override fun create(session: HibernateSession, jobWithToken: VerifiedJobWithAccessToken) {
        val (job, token) = jobWithToken

        val entity = JobInformationEntity(
            job.id,
            job.owner,
            job.application.description.info.toEmbedded(),
            "Verified",
            JobState.VALIDATED,
            job.nodes,
            job.tasksPerNode,
            job.jobInput.asMap(),
            job.files,
            job.maxTime.hours,
            job.maxTime.minutes,
            job.maxTime.seconds,
            job.backend,
            token,
            Date(System.currentTimeMillis()),
            Date(System.currentTimeMillis())
        )

        session.save(entity)
    }

    override fun updateStatus(session: HibernateSession, systemId: String, status: String) {
        val entity = JobInformationEntity[session, systemId] ?: throw JobException.NotFound("job: $systemId")
        entity.status = status
        session.update(entity)
    }

    override fun updateState(session: HibernateSession, systemId: String, state: JobState) {
        val entity = JobInformationEntity[session, systemId] ?: throw JobException.NotFound("job: $systemId")
        entity.state = state
        session.update(entity)
    }

    override fun findOrNull(
        session: HibernateSession,
        systemId: String,
        owner: String?
    ): VerifiedJobWithAccessToken? {
        return JobInformationEntity[session, systemId]
            ?.takeIf { owner == null || it.owner == owner }
            ?.toModel(session)
    }

    private fun JobInformationEntity.toModel(session: HibernateSession): VerifiedJobWithAccessToken =
        VerifiedJobWithAccessToken(
            VerifiedJob(
                appDao.findByNameAndVersion(session, owner, application.name, application.version),
                files,
                systemId,
                owner,
                nodes,
                tasksPerNode,
                SimpleDuration(maxTimeHours, maxTimeMinutes, maxTimeSeconds),
                VerifiedJobInput(parameters),
                backendName
            ),
            accessToken
        )

    private fun NameAndVersion.toEmbedded(): EmbeddedNameAndVersion = EmbeddedNameAndVersion(name, version)
}
