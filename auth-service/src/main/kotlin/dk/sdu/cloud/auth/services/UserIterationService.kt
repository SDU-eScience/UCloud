package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.StatelessSession
import org.hibernate.annotations.NaturalId
import org.slf4j.Logger
import java.awt.Cursor
import java.util.*
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
    private val cursorStateDao: CursorStateDao<HibernateSession>
) {
    private data class OpenIterator(
        val state: CursorState,
        val session: StatelessSession,
        val iterator: ScrollableResults
    )

    private val openIterators: MutableMap<String, OpenIterator> = HashMap()

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
        }
    }

    // Note: Tweak this, if needed.
    private fun nextExpiresAt(): Long =
        System.currentTimeMillis() + 1000L * 60 * 30

    private fun fetchLocal(id: String): List<Principal> {
        val state = openIterators[id] ?: throw UserIterationException.BadIterator()
        val result = ArrayList<Principal>()
        val it = state.iterator
        while (it.next() || result.size >= PAGE_SIZE) {
            result.add((it.get(0) as PrincipalEntity).toModel())
        }
        return result
    }

    suspend fun fetchNext(id: String): List<Principal> {
        if (id in openIterators) {
            return fetchLocal(id)
        } else {
            TODO()
        }
    }

    suspend fun close(id: String) {
        if (id in openIterators) {
            return closeLocal(id)
        } else {
            TODO()
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        const val PAGE_SIZE = 1000
    }
}

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

    }

    private fun CursorState.toEntity(): CursorStateEntity {
        return CursorStateEntity(id, hostname, port, Date(expiresAt))
    }

    private fun CursorStateEntity.toModel(): CursorState {
        return CursorState(id, hostname, port, expiresAt.time)
    }
}
