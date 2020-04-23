package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
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
    var jobId: String,

    var machineReservationCpu: Int?,
    var machineReservationGpu: Int?,
    var machineReservationMem: Int?,
    var machineReservationName: String?,
    var projectId: String?
) {
    companion object : HibernateEntity<JobCompletedEntity>, WithId<String>
}

sealed class ComputeUser {
    data class User(val username: String) : ComputeUser()
    data class Project(val id: String) : ComputeUser()
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

    fun computeUsage(session: HibernateSession, user: ComputeUser): Long {
        val query = session.createCriteriaBuilder<Pair<Long, Int>, JobCompletedEntity>().run {
            criteria.multiselect(entity[JobCompletedEntity::durationInMs], entity[JobCompletedEntity::nodes])
            when (user) {
                is ComputeUser.User -> {
                    criteria.where(
                        (entity[JobCompletedEntity::startedBy] equal user.username)
                    )
                }

                is ComputeUser.Project -> {
                    criteria.where(
                        (entity[JobCompletedEntity::projectId] equal user.id)
                    )
                }
            }
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
    jobId,
    reservation.cpu,
    reservation.gpu,
    reservation.memoryInGigs,
    reservation.name,
    project
)
