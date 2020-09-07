package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.outgoingTargetHost
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A service for iterating all users in the database.
 *
 * Note: This code could later be generalized for all types of DB cursors.
 */
class UserIterationService(
    private val localhostName: String,
    private val localPort: Int,

    private val db: DBContext,
    private val cursorStateDao: CursorStateAsyncDao,

    private val client: RpcClient,
    private val authenticator: RefreshingJWTAuthenticator,
    private val maxConnections: Int = 10
) {
    private lateinit var cleaner: ScheduledExecutorService
    private val mutex = Mutex()

    private data class OpenIterator(
        val state: CursorState,
        val session: AsyncDBConnection
    )

    private val openIterators: MutableMap<String, OpenIterator> = HashMap()

    fun start() {
        if (this::cleaner.isInitialized) throw IllegalStateException()
        cleaner = Executors.newSingleThreadScheduledExecutor()
        cleaner.scheduleAtFixedRate({
            runBlocking {
                try {
                    mutex.withLock {
                        val iterator = openIterators.iterator()
                        while (iterator.hasNext()) {
                            val next = iterator.next()
                            val state = cursorStateDao.findByIdOrNull(db, next.key)

                            if (state == null) {
                                log.debug("Removing ${next.key} (could not find in db)")
                                iterator.remove()
                            } else {
                                if (Time.now() > state.expiresAt) {
                                    log.debug("Removing ${next.key} (expired)")
                                    iterator.remove()
                                }
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn("Caught exception in UserIterationService cleaner thread!")
                    log.warn(ex.stackTraceToString())
                }
            }
        }, 1, 1, TimeUnit.MINUTES)
    }

    fun stop() {
        if (!this::cleaner.isInitialized) throw IllegalStateException()
        cleaner.shutdown()
    }

    suspend fun create(): String {
        require(db is AsyncDBSessionFactory)
        val session = db.openSession()
        db.openTransaction(session)
        mutex.withLock {
            if (openIterators.size >= maxConnections) throw UserIteratorException.TooManyOpen()

            val id = UUID.randomUUID().toString()
            session.sendPreparedStatement(
                """
                    DECLARE curs NO SCROLL CURSOR WITH HOLD
                    FOR SELECT * FROM principals;
                """
            )
            val state = CursorState(id, localhostName, localPort, nextExpiresAt())
            cursorStateDao.create(db, state)
            openIterators[id] = OpenIterator(state, session)
            return id
        }
    }

    private fun closeLocal(id: String) {
        require(db is AsyncDBSessionFactory)
        synchronized(this) {
            val open = openIterators[id] ?: return
            runBlocking {
                open.session
                    .sendPreparedStatement(
                        """
                            CLOSE curs
                        """.trimIndent()
                    )

                db.commit(open.session)
            }
            openIterators.remove(id)
        }
    }

    // Note: Tweak this, if needed.
    private fun nextExpiresAt(): Long =
        Time.now() + 1000L * 60 * 30

    private suspend fun fetchLocal(id: String): List<Principal> {
        val open = openIterators[id] ?: throw UserIterationException.BadIterator()
        return open.session
            .sendPreparedStatement(
                """
                    FETCH FORWARD 1000 FROM curs
                """.trimIndent()
            ).rows.map { it.toPrincipal(false) }
    }


    suspend fun fetchNext(id: String): List<Principal> {
        return if (id in openIterators) {
            fetchLocal(id)
        } else {
            val targetedCloud = findRemoteCloud(id)
            client.call(
                UserDescriptions.fetchNextIterator,
                FindByStringId(id),
                OutgoingHttpCall,
                beforeFilters = { authenticator.authenticateCall(it) },
                afterFilters = { it.attributes.outgoingTargetHost = targetedCloud }
            ).orThrow()
        }
    }

    suspend fun close(id: String) {
        if (id in openIterators) {
            return closeLocal(id)
        } else {
            val targetedCloud = findRemoteCloud(id)
            client.call(
                UserDescriptions.closeIterator,
                FindByStringId(id),
                OutgoingHttpCall,
                beforeFilters = { authenticator.authenticateCall(it) },
                afterFilters = { it.attributes.outgoingTargetHost = targetedCloud }
            )
        }
    }

    private suspend fun findRemoteCloud(id: String): HostInfo {
        val state = cursorStateDao.findByIdOrNull(db, id) ?: throw UserIterationException.BadIterator()

        if (state.hostname == localhostName && state.port == localPort) {
            throw UserIterationException.BadIterator()
        }

        return HostInfo(state.hostname, port = state.port)
    }

    companion object : Loggable {
        override val log: Logger = logger()
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

object CursorStateTable : SQLTable("cursor_state") {
    val id = text("id", notNull = true)
    val hostname = text("hostname", notNull = true)
    val port = int("port", notNull = true)
    val expiresAt = timestamp("expires_at", notNull = true)
}

class CursorStateAsyncDao {
    suspend fun create(db: DBContext, state: CursorState) {
        db.withSession { session ->
            session.insert(CursorStateTable) {
                set(CursorStateTable.id, state.id)
                set(CursorStateTable.hostname, state.hostname)
                set(CursorStateTable.port, state.port)
                set(CursorStateTable.expiresAt, LocalDateTime(state.expiresAt / 1000, DateTimeZone.UTC))
            }
        }
    }

    suspend fun findByIdOrNull(db: DBContext, id: String): CursorState? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM cursor_state
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull()?.toCursorState()
        }
    }

    suspend fun updateExpiresAt(db: DBContext, id: String, newExpiry: Long) {
        db.withSession { session ->
            val rowsAffected = session
                .sendPreparedStatement(
                    {
                        setParameter("time", newExpiry / 1000)
                        setParameter("id", id)
                    },
                    """
                        UPDATE cursor_state
                        SET expires_at = to_timestamp(?time)
                        WHERE id = ?id
                    """.trimIndent()
                ).rowsAffected
            if (rowsAffected != 1L) throw UserIterationException.BadIterator()
        }
    }

    private fun RowData.toCursorState(): CursorState {
        return CursorState(
            getField(CursorStateTable.id),
            getField(CursorStateTable.hostname),
            getField(CursorStateTable.port),
            getField(CursorStateTable.expiresAt).toDateTime().millis
        )
    }
}

sealed class UserIteratorException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class TooManyOpen : UserIteratorException("Too many iterators currently open", HttpStatusCode.Conflict)
}
