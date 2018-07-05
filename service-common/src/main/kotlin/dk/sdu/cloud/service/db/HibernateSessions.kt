package dk.sdu.cloud.service.db

import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder

typealias HibernateSession = org.hibernate.Session

class HibernateSessionFactory(private val factory: SessionFactory) : DBSessionFactory<HibernateSession> {
    override fun <R> withSession(closure: HibernateSession.() -> R): R {
        val session = factory.openSession()
        return session.use(closure)
    }

    override fun <R> withTransaction(
        session: HibernateSession,
        autoCommit: Boolean,
        closure: (HibernateSession) -> R
    ): R {
        with(session) {
            beginTransaction()
            val result = closure(this)
            if (autoCommit) transaction.commit()
            return result
        }
    }

    override fun commit(session: HibernateSession) {
        session.transaction.commit()
    }

    override fun close() {
        factory.close()
    }

    companion object {
        fun create(): HibernateSessionFactory {
            val registry = StandardServiceRegistryBuilder().configure().build()
            return (try {
                MetadataSources(registry).buildMetadata().buildSessionFactory()
            } catch (ex: Exception) {
                StandardServiceRegistryBuilder.destroy(registry)
                throw ex
            }).let { HibernateSessionFactory(it) }
        }
    }
}
