package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(name = "jobs")
class JobEntity(
    var slurmId: Long,

    @Id
    @NaturalId
    var systemId: String
)

class JobHibernateDao : JobDao<HibernateSession> {
    override fun insertMapping(session: HibernateSession, systemId: String, slurmId: Long) {
        val entity = JobEntity(slurmId, systemId)
        session.save(entity)
    }

    override fun resolveSystemId(session: HibernateSession, slurmId: Long): String? {
        return session.criteria<JobEntity> { entity[JobEntity::slurmId] equal slurmId }.list().firstOrNull()?.systemId
    }

    override fun resolveSlurmId(session: HibernateSession, systemId: String): Long? {
        return session.criteria<JobEntity> { entity[JobEntity::systemId] equal systemId }.list().firstOrNull()?.slurmId
    }
}
