package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.accounting.storage.api.StorageForUser
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import java.beans.Expression
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType

@Entity
@Table(name = "storage_usage_for_user")
class StorageForUserEntity(
    var user: String,

    @Temporal(TemporalType.DATE)
    var date: Date,

    var usage: Long,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<StorageForUserEntity>, WithId<Long>
}

fun StorageForUserEntity.toModel() : StorageForUser = StorageForUser(id, user, date, usage)

class StorageAccountingHibernateDao : StorageAccountingDao<HibernateSession> {
    override fun insert(session: HibernateSession, user: Principal, usage: Long) {
        val entity = StorageForUserEntity(user.id, Date(), usage)
        session.save(entity)
    }

    override fun findAllByUserId(
        session: HibernateSession,
        user: Principal,
        paginationRequest: NormalizedPaginationRequest
    ): Page<StorageForUser> {
        return session.paginatedCriteria<StorageForUserEntity>(
            paginationRequest,
            orderBy = { listOf(ascending(entity[StorageForUserEntity::date])) }
        ) {
            allOf(
                entity[StorageForUserEntity::user] equal user.id
            )
        }.mapItems {
            it.toModel()
        }
    }
}
