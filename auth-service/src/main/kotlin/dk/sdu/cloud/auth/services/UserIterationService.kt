package dk.sdu.cloud.auth.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.updateCriteria
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.StatelessSession
import org.hibernate.annotations.NaturalId
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import kotlin.collections.ArrayList

/**
 * A service for iterating all users in the database.
 *
 * Note: This code could later be generalized for all types of DB cursors.
 */
class UserIterationService(
    private val localhostName: String,
    private val localPort: Int,

    private val db: HibernateSessionFactory,
    private val cursorStateDao: CursorStateDao<HibernateSession>,

    private val serviceCloud: RefreshingJWTAuthenticatedCloud
) {
    private lateinit var cleaner: ScheduledExecutorService

    private data class OpenIterator(
        val state: CursorState,
        val session: StatelessSession,
        val iterator: ScrollableResults
    )

    private val openIterators: MutableMap<String, OpenIterator> = HashMap()

    fun start() {
        if (this::cleaner.isInitialized) throw IllegalStateException()
        cleaner = Executors.newSingleThreadScheduledExecutor()
        cleaner.scheduleAtFixedRate({
            log.debug("Running clean up script")
            synchronized(this) {
                val iterator = openIterators.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    val state = db.withTransaction {
                        cursorStateDao.findByIdOrNull(it, next.key)
                    }

                    if (state == null) {
                        log.debug("Removing ${next.key} (could not find in db)")
                        iterator.remove()
                    } else {
                        if (System.currentTimeMillis() > state.expiresAt) {
                            log.debug("Removing ${next.key} (expired)")
                            iterator.remove()
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES)
    }

    fun stop() {
        if (!this::cleaner.isInitialized) throw IllegalStateException()
        cleaner.shutdown()
    }

    fun create(): String {
        val id = UUID.randomUUID().toString()
        val session = db.openStatelessSession()
        val iterator = session.createQuery("from PrincipalEntity").scroll(ScrollMode.FORWARD_ONLY)
        val state = CursorState(id, localhostName, localPort, nextExpiresAt())

        db.withTransaction {
            cursorStateDao.create(
                it,
                state
            )
        }

        synchronized(this) {
            openIterators[id] = OpenIterator(state, session, iterator)
        }
        return id
    }

    private fun closeLocal(id: String) {
        synchronized(this) {
            val open = openIterators[id] ?: return
            runCatching { open.iterator.close() }
            runCatching { open.session.close() }.getOrThrow()

            openIterators.remove(id)
        }
    }

    // Note: Tweak this, if needed.
    private fun nextExpiresAt(): Long =
        System.currentTimeMillis() + 1000L * 60 * 30

    private fun fetchLocal(id: String): List<Principal> {
        val state = openIterators[id] ?: throw UserIterationException.BadIterator()
        val result = ArrayList<Principal>()
        val it = state.iterator
        while (result.size < PAGE_SIZE && it.next()) {
            val nextRow = it.get(0) as? PrincipalEntity ?: break
            result.add(nextRow.toModel())
        }
        return result
    }

    suspend fun fetchNext(id: String): List<Principal> {
        return if (id in openIterators) {
            fetchLocal(id)
        } else {
            val targetedCloud = findRemoteCloud(id)
            UserDescriptions.fetchNextIterator.call(FindByStringId(id), targetedCloud).orThrow()
        }
    }

    suspend fun close(id: String) {
        if (id in openIterators) {
            return closeLocal(id)
        } else {
            val targetedCloud = findRemoteCloud(id)
            UserDescriptions.closeIterator.call(FindByStringId(id), targetedCloud).orThrow()
        }
    }

    private fun findRemoteCloud(id: String): AuthenticatedCloud {
        val state = db.withTransaction {
            cursorStateDao.findByIdOrNull(it, id)
        } ?: throw UserIterationException.BadIterator()

        if (state.hostname == localhostName && state.port == localPort) {
            throw UserIterationException.BadIterator()
        }

        return cloudForHostname(state.hostname, state.port)
    }

    private fun cloudForHostname(hostname: String, port: Int): AuthenticatedCloud {
        val refresher = serviceCloud.tokenRefresher
        val cloudContext = SDUCloud("http://$hostname:$port")
        return RefreshingJWTAuthenticatedCloud(cloudContext, refresher).withJobId()
    }

    companion object : Loggable {
        override val log: Logger = logger()

        const val PAGE_SIZE = 1000
    }
}

private class AuthenticatedCloudWithJobId(private val delegate: AuthenticatedCloud) : AuthenticatedCloud {
    override val parent: CloudContext
        get() = delegate.parent

    override fun HttpRequestBuilder.configureCall() {
        with (delegate) {
            configureCall()
        }

        header("Job-Id", UUID.randomUUID().toString())
    }
}

private fun AuthenticatedCloud.withJobId(): AuthenticatedCloud = AuthenticatedCloudWithJobId(this)

sealed class UserIterationException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class BadIterator : UserIterationException("Bad iterator (Not found)", HttpStatusCode.NotFound)
}

data class CursorState(
    val id: String,
    val hostname: String,
    val port: Int,
    val expiresAt: Long
)

interface CursorStateDao<Session> {
    fun create(session: Session, state: CursorState)
    fun findByIdOrNull(session: Session, id: String): CursorState?
    fun updateExpiresAt(session: Session, id: String, newExpiry: Long)
}

@Entity
@Table(name = "cursor_state")
data class CursorStateEntity(
    @Id
    @NaturalId
    val id: String,

    val hostname: String,
    val port: Int,

    @Temporal(TemporalType.TIMESTAMP)
    val expiresAt: Date
) {
    companion object : HibernateEntity<CursorStateEntity>, WithId<String>
}

class CursorStateHibernateDao : CursorStateDao<HibernateSession> {
    override fun create(session: HibernateSession, state: CursorState) {
        session.save(state.toEntity())
    }

    override fun findByIdOrNull(session: HibernateSession, id: String): CursorState? {
        return CursorStateEntity[session, id]?.toModel()
    }

    override fun updateExpiresAt(session: HibernateSession, id: String, newExpiry: Long) {
        session.updateCriteria<CursorStateEntity>(
            setProperties = {
                criteria.set(entity[CursorStateEntity::expiresAt], Date(newExpiry))
            },

            where = {
                entity[CursorStateEntity::id] equal id
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw UserIterationException.BadIterator()
    }

    private fun CursorState.toEntity(): CursorStateEntity {
        return CursorStateEntity(id, hostname, port, Date(expiresAt))
    }

    private fun CursorStateEntity.toModel(): CursorState {
        return CursorState(id, hostname, port, expiresAt.time)
    }
}
