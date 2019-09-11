package dk.sdu.cloud.task.services

import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.updateCriteria
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType

@Entity
@Table(name = "subscriptions")
data class SubscriptionEntity(
    var hostname: String,
    var port: Int,
    var username: String,
    @Temporal(TemporalType.TIMESTAMP)
    var lastPing: Date,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<SubscriptionEntity>, WithId<Long>
}

class SubscriptionHibernateDao : SubscriptionDao<HibernateSession> {
    override fun open(session: HibernateSession, username: String, hostname: String, port: Int): Long {
        val entity = SubscriptionEntity(hostname, port, username, Date())
        return session.save(entity) as Long
    }

    override fun close(session: HibernateSession, id: Long) {
        session.deleteCriteria<SubscriptionEntity> { entity[SubscriptionEntity::id] equal id }.executeUpdate()
    }

    override fun findConnections(session: HibernateSession, username: String): List<Subscription> {
        val earliestAllowedPing = Date(System.currentTimeMillis() - SubscriptionService.MAX_MS_SINCE_LAST_PING)
        return session
            .criteria<SubscriptionEntity> {
                (entity[SubscriptionEntity::username] equal username) and
                        (entity[SubscriptionEntity::lastPing] greaterThan earliestAllowedPing)
            }
            .list()
            .map {
                Subscription(HostInfo(it.hostname, port = it.port), it.username, it.id!!)
            }
    }

    override fun refreshSessions(session: HibernateSession, hostname: String, port: Int) {
        session.updateCriteria<SubscriptionEntity>(
            where = {
                (entity[SubscriptionEntity::hostname] equal hostname) and (entity[SubscriptionEntity::port] equal port)
            },

            setProperties = {
                criteria.set(entity[SubscriptionEntity::lastPing], Date())
            }
        ).executeUpdate()
    }
}
