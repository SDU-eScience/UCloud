package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.criteria.Predicate

/**
 * @see [AccountingJobCompletedEvent]
 */
@Entity
@Table(name = "job_completed_events")
class JobCompletedEntity(
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
class CompletedJobsHibernateDao : CompletedJobsDao<HibernateSession> {
    override fun upsert(session: HibernateSession, event: AccountingJobCompletedEvent) {
        val existing = JobCompletedEntity[session, event.jobId]
        if (existing != null) return

        val entity = event.toEntity()
        session.saveOrUpdate(entity)
    }

    override fun listAllEvents(
        session: HibernateSession,
        context: ContextQuery,
        user: String
    ): List<AccountingJobCompletedEvent> {
        return session.criteria<JobCompletedEntity>(
            orderBy = { listOf(descinding(entity[JobCompletedEntity::timestamp])) },

            predicate = {
                (entity[JobCompletedEntity::startedBy] equal user) and matchingContext(context)
            }
        ).list().map { it.toModel() }
    }

    override fun listEvents(
        session: HibernateSession,
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<AccountingJobCompletedEvent> {
        return session.paginatedCriteria<JobCompletedEntity>(
            paging,
            orderBy = { listOf(descinding(entity[JobCompletedEntity::timestamp])) },
            predicate = {
                (entity[JobCompletedEntity::startedBy] equal user) and matchingContext(context)
            }
        ).mapItems { it.toModel() }
    }

    override fun computeUsage(session: HibernateSession, context: ContextQuery, user: String): Long {
        return session.createCriteriaBuilder<Long, JobCompletedEntity>().run {
            criteria.select(sum(entity[JobCompletedEntity::durationInMs]))
            criteria.where(
                (entity[JobCompletedEntity::startedBy] equal user) and matchingContext(context)
            )
            criteria
        }.createQuery(session)
            .list()
            ?.singleOrNull() ?: 0L
    }

    private fun CriteriaBuilderContext<*, JobCompletedEntity>.matchingContext(context: ContextQuery): Predicate {
        var builder = literal(true).toPredicate()
        // TODO We really need >= and <=
        val since = context.since
        val until = context.until
        if (since != null) {
            builder = builder and (entity[JobCompletedEntity::timestamp] greaterThan Date(since - 1))
        }

        if (until != null) {
            builder = builder and (entity[JobCompletedEntity::timestamp] lessThan Date(until + 1))
        }

        return builder
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

/**
 * Converts [JobCompletedEntity] to [AccountingJobCompletedEvent]
 */
fun JobCompletedEntity.toModel(): AccountingJobCompletedEvent = AccountingJobCompletedEvent(
    NameAndVersion(applicationName, applicationVersion),
    nodes,
    durationInMs.toSimpleDuration(),
    startedBy,
    jobId,
    timestamp.time
)
