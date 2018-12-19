package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.criteria.Predicate

@Entity
@Table(
    name = "storage_usage_for_user",
    indexes = [Index(columnList = "username")]
)
class StorageForUserEntity(
    @Column(name = "username")
    var username: String,

    @Temporal(TemporalType.TIMESTAMP)
    var date: Date,

    var usage: Long,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<StorageForUserEntity>, WithId<Long>
}

fun StorageForUserEntity.toModel() : StorageUsedEvent = StorageUsedEvent(date.time, usage, id, username)

fun StorageUsedEvent.toEntity() : StorageForUserEntity = StorageForUserEntity(user, Date(timestamp), bytesUsed, id)

class StorageAccountingHibernateDao : StorageAccountingDao<HibernateSession> {

    override fun insert(session: HibernateSession, user: Principal, usage: Long) {
        val entity = StorageForUserEntity(user.id, Date(), usage)
        session.save(entity)
    }

    override fun findAllByUserId(
        session: HibernateSession,
        user: String,
        paginationRequest: NormalizedPaginationRequest
    ): Page<StorageUsedEvent> {
        return session.paginatedCriteria<StorageForUserEntity>(
            paginationRequest,
            orderBy = { listOf(ascending(entity[StorageForUserEntity::date])) }
        ) {
            allOf(
                entity[StorageForUserEntity::username] equal user
            )
        }.mapItems {
            it.toModel()
        }
    }

    override fun findAllPage(
        session: HibernateSession,
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<StorageUsedEvent> {
        return session.paginatedCriteria<StorageForUserEntity>(
            paging,
            orderBy = { listOf(descending(entity[StorageForUserEntity::date])) } ,
            predicate = {
                (entity[StorageForUserEntity::username] equal user) and matchingContext(context)
            }
        ).mapItems {
            it.toModel()
        }
    }

    override fun findAllList(
        session: HibernateSession,
        context: ContextQuery,
        user: String
    ): List<StorageUsedEvent> {
        return session.criteria<StorageForUserEntity>(
            orderBy = { listOf(ascending(entity[StorageForUserEntity::date])) } ,
            predicate = {
                (entity[StorageForUserEntity::username] equal user) and matchingContext(context)
            }
        ).list().map {
            it.toModel()
        }
    }

    private fun CriteriaBuilderContext<*, StorageForUserEntity>.matchingContext(context: ContextQuery): Predicate {
        var builder = literal(true).toPredicate()
        // TODO We really need >= and <=
        val since = context.since
        val until = context.until
        if (since != null) {
            builder = builder and (entity[StorageForUserEntity::date] greaterThan Date(since - 1))
        }

        if (until != null) {
            builder = builder and (entity[StorageForUserEntity::date] lessThan Date(until + 1))
        }

        return builder
    }
}
