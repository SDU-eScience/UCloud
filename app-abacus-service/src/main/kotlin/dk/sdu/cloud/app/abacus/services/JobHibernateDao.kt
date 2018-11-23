package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import io.ktor.http.HttpStatusCode
import org.hibernate.NonUniqueObjectException
import org.hibernate.annotations.NaturalId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "jobs")
class JobEntity(
    var slurmId: Long,

    @Id
    @NaturalId
    var systemId: String
) {
    companion object : HibernateEntity<JobEntity>, WithId<String>
}

class JobHibernateDao : JobDao<HibernateSession> {
    override fun insertMapping(session: HibernateSession, systemId: String, slurmId: Long) {
        val entity = JobEntity(slurmId, systemId)
        try {
            session.save(entity)
        } catch (ex: NonUniqueObjectException) {
            throw JobException.NotUniqueID()
        }
    }

    override fun resolveSystemId(session: HibernateSession, slurmId: Long): String? {
        return session.criteria<JobEntity> { entity[JobEntity::slurmId] equal slurmId }.list().firstOrNull()?.systemId
    }

    override fun resolveSlurmId(session: HibernateSession, systemId: String): Long? {
        return session.criteria<JobEntity> { entity[JobEntity::systemId] equal systemId }.list().firstOrNull()?.slurmId
    }
}

sealed class JobException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotUniqueID : JobException("Not Unique", HttpStatusCode.BadRequest)
}
