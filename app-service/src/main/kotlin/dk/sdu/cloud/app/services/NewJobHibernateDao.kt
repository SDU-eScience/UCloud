package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "job_information")
data class NewJobInformationEntity(
    @Id
    @NaturalId
    val systemId: String,

    var owner: String,

    @Embedded
    var application: EmbeddedNameAndVersion,

    var status: String,

    var state: JobState,

    var accessToken: String,

    var nodes: Int,

    @Type(type = JSONB_TYPE)
    var parameters: Map<String, Any?>,

    var backendName: String,

    override var createdAt: Date,

    override var modifiedAt: Date
) : WithTimestamps {
    companion object : HibernateEntity<NewJobInformationEntity>, WithId<String>
}

class NewJobHibernateDao : NewJobDao<HibernateSession> {
    override fun create(session: HibernateSession, job: VerifiedJob) {
        val entity = NewJobInformationEntity(
            job.id,
            job.owner,
            job.application.description.info.toEmbedded(),
            "Verified",
            JobState.VALIDATED,
            job.accessToken,
            job.nodes,
            job.jobInput.asMap(),
            "TODO backend?",
            Date(System.currentTimeMillis()),
            Date(System.currentTimeMillis())
        )

        session.save(entity)
    }

    override fun updateStatus(session: HibernateSession, systemId: String, status: String) {
        val entity = NewJobInformationEntity[session, systemId] ?: throw JobNotFoundException("job: $systemId")
        entity.status = status
        session.update(entity)
    }

    override fun updateState(session: HibernateSession, systemId: String, state: AppState) {
        val entity = NewJobInformationEntity[session, systemId] ?: throw JobNotFoundException("job: $systemId")
        entity.state = state
        session.update(entity)
    }

    override fun findOrNull(session: HibernateSession, systemId: String, owner: String): NewJobInformation? {
        return NewJobInformationEntity[session, systemId]?.takeIf { it.owner == owner }?.toModel()
    }

    private fun NewJobInformationEntity.toModel(): NewJobInformation = TODO()

    private fun NameAndVersion.toEmbedded(): EmbeddedNameAndVersion = EmbeddedNameAndVersion(name, version)
}
