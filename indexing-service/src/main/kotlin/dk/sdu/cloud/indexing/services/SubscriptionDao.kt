package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import java.io.Serializable
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Table

data class Subscription(val subscriber: String, val fileId: String)

interface SubscriptionDao<Session> {
    fun addSubscription(session: Session, subscriber: String, fileIds: Collection<String>)
    fun removeSubscriptions(session: Session, subscriber: String, fileIds: Collection<String>)
    fun findSubscribers(session: Session, fileIds: Collection<String>): List<Subscription>
    fun deleteById(session: Session, deletedFiles: Collection<String>)
}

@Entity
@Table(name = "subscriptions")
data class SubscriptionEntity(
    @get:EmbeddedId
    var id: SubscriptionEntityId
) {
    companion object : HibernateEntity<SubscriptionEntity>, WithId<SubscriptionEntityId>
}

@Embeddable
data class SubscriptionEntityId(var subscriber: String, var fileId: String) : Serializable

class SubscriptionHibernateDao : SubscriptionDao<HibernateSession> {
    override fun addSubscription(session: HibernateSession, subscriber: String, fileIds: Collection<String>) {
        fileIds.forEach { fileId ->
            session.save(SubscriptionEntity(SubscriptionEntityId(subscriber, fileId)))
        }
    }

    override fun removeSubscriptions(session: HibernateSession, subscriber: String, fileIds: Collection<String>) {
        fileIds.chunked(CHUNK_SIZE).forEach { chunk ->
            session.deleteCriteria<SubscriptionEntity> {
                (entity[SubscriptionEntity::id][SubscriptionEntityId::subscriber] equal subscriber) and
                        (entity[SubscriptionEntity::id][SubscriptionEntityId::fileId] isInCollection chunk)
            }.executeUpdate()
        }
    }

    override fun findSubscribers(session: HibernateSession, fileIds: Collection<String>): List<Subscription> {
        return fileIds.chunked(CHUNK_SIZE).flatMap { chunk ->
            session.criteria<SubscriptionEntity> {
                entity[SubscriptionEntity::id][SubscriptionEntityId::fileId] isInCollection chunk
            }.list().map { Subscription(it.id.subscriber, it.id.fileId) }
        }
    }

    override fun deleteById(session: HibernateSession, deletedFiles: Collection<String>) {
        deletedFiles.chunked(CHUNK_SIZE).forEach { chunk ->
            session.deleteCriteria<SubscriptionEntity> {
                (entity[SubscriptionEntity::id][SubscriptionEntityId::fileId]) isInCollection chunk
            }.executeUpdate()
        }
    }

    companion object {
        private const val CHUNK_SIZE = 500
    }
}
