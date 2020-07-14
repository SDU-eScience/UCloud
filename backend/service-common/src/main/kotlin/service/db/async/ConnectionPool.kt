package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.SuspendingConnectionImpl
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.future.await

sealed class DBContext

suspend fun <R> DBContext.withSession(block: suspend (session: AsyncDBConnection) -> R): R {
    return when (this) {
        is AsyncDBSessionFactory -> {
            withTransaction<R, AsyncDBConnection> { session ->
                block(session)
            }
        }

        is AsyncDBConnection -> {
            block(this)
        }
    }
}

data class AsyncDBConnection(val conn: SuspendingConnectionImpl) : DBContext(), SuspendingConnection by conn

/**
 * A [DBSessionFactory] for the jasync library.
 */
class AsyncDBSessionFactory(config: DatabaseConfig) : DBSessionFactory<AsyncDBConnection>, DBContext() {
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
    }

    override suspend fun commit(session: AsyncDBConnection) {
        session.sendQuery("commit")
    }

    override suspend fun flush(session: AsyncDBConnection) {
        // No-op
    }

    override suspend fun openSession(): AsyncDBConnection {
        return AsyncDBConnection(pool.take().await().asSuspending as SuspendingConnectionImpl)
    }

    override suspend fun rollback(session: AsyncDBConnection) {
        session.sendQuery("rollback")
    }

    override suspend fun openTransaction(session: AsyncDBConnection) {
        // We always begin by setting the search_path to our schema. The schema is checked in the init block to make
        // this safe.
        session.sendQuery("set search_path to \"$schema\"")
        session.sendQuery("begin")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
