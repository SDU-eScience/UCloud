package dk.sdu.cloud.share.services.db

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.SuspendingConnectionImpl
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.POSTGRES_9_5_DIALECT
import dk.sdu.cloud.service.db.POSTGRES_DRIVER
import kotlinx.coroutines.future.await

typealias AsyncDBConnection = SuspendingConnection

class AsyncDBSessionFactory(config: DatabaseConfig) : DBSessionFactory<AsyncDBConnection> {
    private val schema = config.defaultSchema
    init {
        require(schema.matches(columnRegex)) { "Potentially bad schema passed: $schema" }
    }

    private val pool = run {
        if (config.dialect != POSTGRES_9_5_DIALECT && config.driver != POSTGRES_DRIVER) {
            log.warn("Bad configuration: $config")
            throw IllegalArgumentException("Cannot create an AsyncDBSessionFactory for non postgres databases!")
        }

        val username = config.username ?: throw IllegalArgumentException("No credentials")
        val password = config.password ?: throw IllegalArgumentException("No credentials")
        val jdbcUrl = config.jdbcUrl ?: throw IllegalArgumentException("No connection string")

        PostgreSQLConnectionBuilder.createConnectionPool(jdbcUrl) {
            this.maxActiveConnections = 50
            this.maxIdleTime = 30_000
            this.username = username
            this.password = password
        }
    }

    override suspend fun close() {
        pool.disconnect().await()
    }

    override suspend fun closeSession(session: AsyncDBConnection) {
        pool.giveBack((session as SuspendingConnectionImpl).connection as PostgreSQLConnection)
    }

    override suspend fun commit(session: AsyncDBConnection) {
        session.sendQuery("COMMIT")
    }

    override suspend fun flush(session: AsyncDBConnection) {
        // No-op
    }

    override suspend fun openSession(): AsyncDBConnection {
        return pool.take().await().asSuspending
    }

    override suspend fun rollback(session: AsyncDBConnection) {
        session.sendQuery("ROLLBACK")
    }

    override suspend fun openTransaction(session: AsyncDBConnection) {
        session.sendPreparedStatement("set search_path to $schema")
        session.sendQuery("BEGIN")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
