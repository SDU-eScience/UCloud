package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.SuspendingConnectionImpl
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AtomicInteger
import dk.sdu.cloud.calls.server.jobIdOrNull
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.micro.featureOrNull
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.util.concurrent.atomic.AtomicBoolean

sealed class TransactionMode {
    abstract val readWrite: Boolean
    abstract fun toSql(): String
    protected fun readWriteToSql(): String = if (readWrite) "read write" else "read only"

    class Serializable(override val readWrite: Boolean) : TransactionMode() {
        override fun toSql(): String = "serializable ${readWriteToSql()}"
    }
    class RepeatableRead(override val readWrite: Boolean) : TransactionMode() {
        override fun toSql(): String = "repeatable read ${readWriteToSql()}"
    }
    class ReadCommitted(override val readWrite: Boolean) : TransactionMode() {
        override fun toSql(): String = "read committed ${readWriteToSql()}"
    }
    class ReadUncommitted(override val readWrite: Boolean) : TransactionMode() {
        override fun toSql(): String = "read uncommitted ${readWriteToSql()}"
    }
}

sealed class DBContext

suspend fun <R> DBContext.withSession(
    remapExceptions: Boolean = false,
    transactionMode: TransactionMode? = null,
    restartTransactionsOnSerializationFailure: Boolean = true,
    block: suspend (session: AsyncDBConnection) -> R
): R {
    var attempts = 0
    while (true) {
        try {
            return when (this) {
                is AsyncDBSessionFactory -> {
                    withTransaction<R, AsyncDBConnection>(transactionMode = transactionMode) { session ->
                        block(session)
                    }
                }

                is AsyncDBConnection -> {
                    block(this)
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (remapExceptions) {
                when (ex.errorCode) {
                    PostgresErrorCodes.EXCLUSION_VIOLATION,
                    PostgresErrorCodes.UNIQUE_VIOLATION -> {
                        throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
                    }

                    PostgresErrorCodes.RAISE_EXCEPTION -> {
                        throw RPCException(ex.errorMessage.message ?: "", HttpStatusCode.BadRequest)
                    }

                    PostgresErrorCodes.SERIALIZATION_FAILURE -> {
                        attempts++
                        if (restartTransactionsOnSerializationFailure && attempts < 10) {
                            delay(100)
                            continue
                        }
                        throw ex
                    }
                }
            }

            throw ex
        }
    }
}

data class AsyncDBConnection(
    internal val conn: SuspendingConnectionImpl, // Internal jasync-sql connection
    val context: DebugContext,
    internal val debug: DebugSystem? = null,
) : DBContext(), SuspendingConnection by conn

/**
 * A [DBSessionFactory] for the jasync library.
 */
class AsyncDBSessionFactory(
    config: DatabaseConfig,
    private val debug: DebugSystem? = null
) : DBSessionFactory<AsyncDBConnection>, DBContext() {
    constructor(micro: Micro) : this(micro.databaseConfig, micro.featureOrNull(DebugSystem))

    private val schema = config.defaultSchema

    init {
        require(schema.matches(safeSqlNameRegex)) { "Potentially bad schema passed: $schema" }
    }

    private val pool = run {
        val username = config.username ?: throw IllegalArgumentException("Missing credentials")
        val password = config.password ?: throw IllegalArgumentException("Missing credentials")
        val jdbcUrl = config.jdbcUrl ?: throw IllegalArgumentException("Missing connection string")

        PostgreSQLConnectionBuilder.createConnectionPool(jdbcUrl) {
            this.maxActiveConnections = config.poolSize ?: 50
            this.maxIdleTime = 30_000
            this.username = username
            this.password = password
        }
    }

    override suspend fun close() {
        pool.disconnect().await()
    }

    override suspend fun closeSession(session: AsyncDBConnection) {
        pool.giveBack((session.conn).connection as PostgreSQLConnection)

        debug?.sendMessage(
            DebugMessage.DatabaseConnection(
                session.context,
                isOpen = false
            )
        )
    }

    override suspend fun commit(session: AsyncDBConnection) {
        session.sendQuery("commit")
        debug?.sendMessage(
            DebugMessage.DatabaseTransaction(
                session.context,
                DebugMessage.DBTransactionEvent.COMMIT
            )
        )
    }

    override suspend fun flush(session: AsyncDBConnection) {
        // No-op
    }

    override suspend fun openSession(): AsyncDBConnection {
        val context = DebugContext.Job(baseId + sessionId.getAndIncrement(), rpcContext()?.call?.jobIdOrNull)
        val result = AsyncDBConnection(
            pool.take().await().asSuspending as SuspendingConnectionImpl,
            context,
            debug,
        )

        debug?.sendMessage(DebugMessage.DatabaseConnection(
            context,
            isOpen = true
        ))

        return result
    }

    override suspend fun rollback(session: AsyncDBConnection) {
        session.sendQuery("rollback")
        debug?.sendMessage(
            DebugMessage.DatabaseTransaction(
                session.context,
                DebugMessage.DBTransactionEvent.ROLLBACK
            )
        )
    }

    override suspend fun openTransaction(session: AsyncDBConnection, transactionMode: TransactionMode?) {
        debug?.sendMessage(
            DebugMessage.DatabaseTransaction(
                session.context,
                DebugMessage.DBTransactionEvent.OPEN
            )
        )

        // We always begin by setting the search_path to our schema. The schema is checked in the init block to make
        // this safe.
        if (setJitOff.get()) {
            try {
                session.sendQuery("set jit = off")
            } catch (ex: Throwable) {
                setJitOff.set(false)
            }
        }

        session.sendQuery("set search_path to \"$schema\",public")
        if (transactionMode == null) {
            session.sendQuery("begin")
        } else {
            session.sendQuery("begin transaction isolation level ${transactionMode.toSql()}")
        }
    }

    companion object : Loggable {
        override val log = logger()

        val baseId = "DB-"
        private val sessionId = AtomicInteger(0)
        private val setJitOff = AtomicBoolean(true)
    }
}
