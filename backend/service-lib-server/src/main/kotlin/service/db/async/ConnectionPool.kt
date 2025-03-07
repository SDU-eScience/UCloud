package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.SuspendingConnectionImpl
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AtomicInteger
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.withHardTimeout
import dk.sdu.cloud.systemName
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.HashSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

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
object DiscardingDBContext : DBContext()
object FakeDBContext : DBContext()
private const val DEBUG_ERRORS = false

suspend fun <R> DBContext.withSession(
    remapExceptions: Boolean = false,
    transactionMode: TransactionMode? = null,
    restartTransactionsOnSerializationFailure: Boolean = true,
    reason: String = "unknown",
    block: suspend (session: AsyncDBConnection) -> R
): R {
    var realReason = reason
    var caller: StackWalker.StackFrame? = null
    if (DEBUG_ERRORS) {
        caller = StackWalker.getInstance().walk { sequence ->
            sequence
                .filter { 
                    val className = it.getClassName()
                    className.startsWith("dk.sdu.cloud") && !className.startsWith("dk.sdu.cloud.service.db.async")
                }
                .findFirst()
                .orElse(null)
        }

        if (caller != null) {
            realReason = "${caller.fileName}:${caller.lineNumber} ${caller.methodName}"
        }
    }

    var attempts = 0
    while (true) {
        try {
            return when (this) {
                is AsyncDBSessionFactory -> {
                    withTransaction<R, AsyncDBConnection>(reason = realReason, transactionMode = transactionMode) { session ->
                        block(session)
                    }
                }

                is AsyncDBConnection -> {
                    block(this)
                }

                FakeDBContext -> error("Cannot call withSession on a FakeDBContext")
                DiscardingDBContext -> {
                    Unit as? R ?: throw IllegalStateException("Unable to use a fake discarding DB when withSession needs to return a result")
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

            if (DEBUG_ERRORS) {
                println("Postgres error from: $caller")
            }

            throw ex
        }
    }
}

typealias DBTransaction = AsyncDBConnection
data class AsyncDBConnection(
    internal val conn: SuspendingConnectionImpl, // Internal jasync-sql connection
    internal val debug: DebugSystemFeature,
) : DBContext(), SuspendingConnection by conn {
    val mutex: Mutex = Mutex()
    var reason: String = ""

    fun registerNotifyListener(handler: (channel: String, payload: String) -> Unit) {
        (conn.connection as PostgreSQLConnection).registerNotifyListener { resp ->
            handler(resp.channel, resp.payload)
        }
    }

    fun clearNotifyListeners() {
        (conn.connection as PostgreSQLConnection).clearNotifyListeners()
    }
}

/**
 * A [DBSessionFactory] for the jasync library.
 */
class AsyncDBSessionFactory(
    config: DatabaseConfig,
    internal val debug: DebugSystemFeature
) : DBSessionFactory<AsyncDBConnection>, DBContext() {
    constructor(micro: Micro) : this(micro.databaseConfig, micro.feature(DebugSystemFeature))

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
            this.maximumMessageSize = 1024 * 1024 * 32
        }
    }

    override suspend fun close() {
        pool.disconnect().await()
    }

    override suspend fun closeSession(session: AsyncDBConnection) {
        inflightTransactions.dec()
        knownReasonsMutex.withLock {
            knownReasons.add(session.reason)
        }
        pool.giveBack((session.conn).connection as PostgreSQLConnection)
    }

    override suspend fun commit(session: AsyncDBConnection) {
        session.sendQuery("commit")
        debug.system.databaseTransaction(
            MessageImportance.THIS_IS_NORMAL,
            DBTransactionEvent.COMMIT
        )
    }

    override suspend fun flush(session: AsyncDBConnection) {
        // No-op
    }

    val knownReasons = HashSet<String>()
    val knownReasonsMutex = Mutex()

    override suspend fun openSession(reason: String): AsyncDBConnection {
        return withHardTimeout(15_000, { "Opening database session" }, hardDeadline = 10000) {
            val result = AsyncDBConnection(
                pool.take().await().asSuspending as SuspendingConnectionImpl,
                debug,
            )
            inflightTransactions.inc()
            result.reason = reason
            knownReasonsMutex.withLock {
                knownReasons.add(reason)
            }

            //Health check
            val rand = (0..999).random()
            //expected 1 hitrate
            if (rand < 1) {
                withHardTimeout(5_000, { "Health Check session" }) {
                    val selectValue = (0..10000).random()
                    val returnValue = result.sendPreparedStatement(
                        {
                            setParameter("select_val", selectValue)
                        },
                        """
                            select :select_val
                        """
                    ).rows.firstOrNull().let { it?.getInt(0)!! } ?: -1

                    if (returnValue != selectValue) {
                        log.error("Not the correct value returned from test")
                        exitProcess(1)
                    }
                }
            }
            result
        }
    }

    override suspend fun rollback(session: AsyncDBConnection) {
        try {
            session.sendQuery("rollback")
        } catch (ex: Throwable) {}

        debug.system.databaseTransaction(
            MessageImportance.THIS_IS_NORMAL,
            DBTransactionEvent.ROLLBACK
        )
    }

    override suspend fun openTransaction(session: AsyncDBConnection, transactionMode: TransactionMode?) {
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
        private val setJitOff = AtomicBoolean(true)

        private val inflightTransactions = Gauge.build()
            .namespace(systemName)
            .subsystem("database")
            .name("transactions_in_flight")
            .help("Number of transactions currently in-flight")
            .register()

        val transactionDuration = Summary.build()
            .namespace(systemName)
            .subsystem("database")
            .name("transaction_duration")
            .help("Number of transactions currently in-flight")
            .quantile(0.5, 0.01)
            .quantile(0.75, 0.01)
            .quantile(0.95, 0.01)
            .quantile(0.99, 0.01)
            .register()
    }
}
