package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "subscriptions")
data class SubscriptionEntity(
    var hostname: String,
    var port: Int,
    var username: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<SubscriptionEntity>, WithId<Long>
}

class SubscriptionHibernateDao : SubscriptionDao<HibernateSession> {
    override fun open(session: HibernateSession, username: String, hostname: String, port: Int): Long {
        val entity = SubscriptionEntity(hostname, port, username)
        return session.save(entity) as Long
    }

    override fun close(session: HibernateSession, id: Long) {
        session.deleteCriteria<SubscriptionEntity> { entity[SubscriptionEntity::id] equal id }.executeUpdate()
    }

    override fun findConnections(session: HibernateSession, username: String): List<Subscription> {
        return session.criteria<SubscriptionEntity> { entity[SubscriptionEntity::username] equal username }.list().map {
            Subscription(HostInfo(it.hostname, port = it.port), it.username, it.id!!)
        }
    }
}
