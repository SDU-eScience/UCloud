package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * @see [AccountingJobCompletedEvent]
 */
@Entity
@Table(name = "job_completed_events")
data class JobCompletedEntity(
    var applicationName: String,
    var applicationVersion: String,
    var durationInMs: Long,
    var startedBy: String,
    var nodes: Int,
    var timestamp: Date,

    @NaturalId
    @Id
    var jobId: String
) {
    companion object : HibernateEntity<JobCompletedEntity>, WithId<String>
}

/**
 * Implements [CompletedJobsDao] with Hibernate
 */
class CompletedJobsDao {
    fun upsert(session: HibernateSession, event: AccountingJobCompletedEvent) {
        val existing = JobCompletedEntity[session, event.jobId]
        if (existing != null) return

        val entity = event.toEntity()
        session.saveOrUpdate(entity)
    }

    fun computeUsage(session: HibernateSession, user: String): Long {
        val query = session.createCriteriaBuilder<Pair<Long, Int>, JobCompletedEntity>().run {
            criteria.multiselect(entity[JobCompletedEntity::durationInMs], entity[JobCompletedEntity::nodes])
            criteria.where(
                (entity[JobCompletedEntity::startedBy] equal user)
            )
            criteria
        }.createQuery(session).list()

        return query.map { it.first * it.second }.sum()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

/**
 * Converts [AccountingJobCompletedEvent] to [JobCompletedEntity]
 */
fun AccountingJobCompletedEvent.toEntity(): JobCompletedEntity = JobCompletedEntity(
    application.name,
    application.version,
    totalDuration.toMillis(),
    startedBy,
    nodes,
    Date(timestamp),
    jobId
)
