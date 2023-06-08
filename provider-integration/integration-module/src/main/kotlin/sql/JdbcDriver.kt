package dk.sdu.cloud.sql

import dk.sdu.cloud.debug.DBTransactionEvent
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.databaseQuery
import dk.sdu.cloud.debug.databaseResponse
import dk.sdu.cloud.debug.databaseTransaction
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.systemName
import dk.sdu.cloud.utils.forEachIndexedGraal
import dk.sdu.cloud.utils.whileGraal
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.sql.Connection as JavaSqlConnection
import java.sql.PreparedStatement as JavaSqlPreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

class SimpleConnectionPool(val size: Int, private val constructor: (pool: SimpleConnectionPool) -> JdbcConnection) {
    // NOTE(Dan): Super, super simple solution to a connection pool. It should probably be changed, but I don't want
    // to make a decision for a connection pool right now. Not even sure that JDBC and SQLite is the right choice at
    // the moment.
    private val connections = Array(size) { idx -> constructor(this).also { it._connectionTicket = idx } }
    private val isFree = BooleanArray(size) { true }
    private val mutex = Mutex()

    suspend fun borrow(): JdbcConnection {
        // NOTE(Dan): Simple spin lock to find a free instance
        var firstFree = -1
        whileGraal({ currentCoroutineContext().isActive && firstFree == -1 }) {
            mutex.withLock {
                isFree.forEachIndexedGraal { i, b ->
                    if (firstFree == -1 && b) {
                        firstFree = i
                        isFree[i] = false
                    }
                }
            }

            delay(50)
        }

        if (firstFree == -1) error("Could not find a free connection")

        var connection = connections[firstFree]
        if (!connection.isReady()) {
            connections[firstFree] = constructor(this).also { it._connectionTicket = firstFree }
            connection = connections[firstFree]
        }

        inflightTransactions.inc()
        return connection
    }

    suspend fun recycle(instance: JdbcConnection) {
        isFree[instance._connectionTicket] = true
        inflightTransactions.dec()
    }

    companion object {
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

abstract class JdbcDriver : DBContext.ConnectionFactory() {
    protected abstract val pool: SimpleConnectionPool

    override suspend fun openSession(): JdbcConnection {
        return pool.borrow()
    }

    override suspend fun close() {
    }
}

class JdbcConnection(
    private val connection: JavaSqlConnection,
    private val pool: SimpleConnectionPool
) : DBContext.Connection() {
    var _connectionTicket = -1
    init {
        connection.autoCommit = false
    }

    override suspend fun isReady(): Boolean {
        return !connection.isClosed
    }

    override suspend fun close() {
        pool.recycle(this)
    }

    override suspend fun openTransaction() {
        // Already open
    }

    override suspend fun commit() {
        connection.commit()
        debugSystem.databaseTransaction(MessageImportance.IMPLEMENTATION_DETAIL, DBTransactionEvent.COMMIT)
    }

    override suspend fun rollback() {
        connection.rollback()
        debugSystem.databaseTransaction(MessageImportance.THIS_IS_ODD, DBTransactionEvent.ROLLBACK)
    }

    override fun prepareStatement(statement: String): PreparedStatement =
        JdbcPreparedStatement(statement, connection)
}

class JdbcPreparedStatement(
    private val rawStatement: String,
    private val conn: JavaSqlConnection,
) : PreparedStatement {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val preparedStatement: String
    private val parameters: Array<Any?>
    private val debugParameters = HashMap<String, JsonElement>()
    private val statement: JavaSqlPreparedStatement
    private var executeStart: Long = 0

    init {
        val parameterNamesToIndex = HashMap<String, List<Int>>()

        var parameterIndex = 0
        statementInputRegex.findAll(rawStatement).forEach {
            val parameterName = it.groups[2]!!.value
            parameterNamesToIndex[parameterName] =
                (parameterNamesToIndex[parameterName] ?: emptyList()) + listOf(parameterIndex)

            parameterIndex++
        }

        preparedStatement = statementInputRegex.replace(rawStatement) { it.groups[1]!!.value + "?" }
        this.parameterNamesToIndex = parameterNamesToIndex
        parameters = Array(parameterIndex) { null }

        try {
            statement = conn.prepareStatement(preparedStatement)
        } catch (ex: Throwable) {
            throw IllegalArgumentException(buildString {
                appendLine("Unable to prepare statement.")
                appendLine("Raw statement:")
                appendLine(rawStatement.trimIndent().prependIndent("    "))
                appendLine()
                appendLine("Prepared statement:")
                appendLine(preparedStatement.trimIndent().prependIndent("    "))
                appendLine()
                appendLine("Reason: ")
                appendLine((ex.message ?: "None").prependIndent("    "))
            }, ex)
        }
    }

    private fun indices(name: String): List<Int> = parameterNamesToIndex[name] ?: error("Unknown parameter $name")

    override suspend fun bindNull(param: String) {
        debugParameters[param] = JsonNull

        for (idx in indices(param)) {
            statement.setNull(idx + 1, 0)
        }
    }

    override suspend fun bindInt(param: String, value: Int) {
        debugParameters[param] = JsonPrimitive(value)

        for (idx in indices(param)) {
            statement.setInt(idx + 1, value)
        }
    }

    override suspend fun bindLong(param: String, value: Long) {
        debugParameters[param] = JsonPrimitive(value)

        for (idx in indices(param)) {
            statement.setLong(idx + 1, value)
        }
    }

    override suspend fun bindString(param: String, value: String) {
        debugParameters[param] = JsonPrimitive(value)

        for (idx in indices(param)) {
            statement.setString(idx + 1, value)
        }
    }

    override suspend fun bindBoolean(param: String, value: Boolean) {
        debugParameters[param] = JsonPrimitive(value)

        for (idx in indices(param)) {
            statement.setBoolean(idx + 1, value)
        }
    }

    override suspend fun bindDouble(param: String, value: Double) {
        debugParameters[param] = JsonPrimitive(value)

        for (idx in indices(param)) {
            statement.setDouble(idx + 1, value)
        }
    }

    override suspend fun bindList(param: String, value: List<Any?>, typeHint: Any?) {
        debugParameters[param] = JsonArray(
            value.map {
                when (it) {
                    is Boolean -> JsonPrimitive(it)

                    is String -> JsonPrimitive(it)

                    is Short -> JsonPrimitive(it)
                    is Int -> JsonPrimitive(it)
                    is Long -> JsonPrimitive(it)

                    is Float -> JsonPrimitive(it)
                    is Double -> JsonPrimitive(it)

                    else -> JsonNull
                }
            }
        )

        val sqlArray = statement.connection.createArrayOf(
            when (typeHint ?: value.find { it != null }) {
                is Boolean -> "boolean"
                is String -> "text"

                is Short -> "int2"
                null, is Int -> "int4"
                is Long -> "int8"

                is Float -> "float4"
                is Double -> "float8"

                else -> error("Not supported to happen")
            },
            value.toTypedArray()
        )

        for (idx in indices(param)) {
            statement.setArray(idx + 1, sqlArray)
        }
    }

    private fun willReturnResults(): Boolean {
        return try {
            (statement.metaData?.columnCount ?: 0) > 0
        } catch (ex: SQLException) {
            false
        }
    }

    override suspend fun execute(isUpdateHint: Boolean?): ResultCursor {
        val isUpdate = if (isUpdateHint == true) true else if (isUpdateHint == false) false else !willReturnResults()

        debugSystem.databaseQuery(
            MessageImportance.THIS_IS_NORMAL,
            JsonObject(debugParameters),
            rawStatement
        )

        executeStart = Time.now()
        return if (isUpdate) {
            JdbcUpdateCursor(statement.executeUpdate())
        } else {
            JdbcResultCursor(statement.executeQuery())
        }
    }

    override suspend fun reset() {
        statement.clearParameters()
    }

    override suspend fun close() {
        statement.close()

        val responseTime = Time.now() - executeStart
        debugSystem.databaseResponse(
            when {
                responseTime >= 300 -> MessageImportance.THIS_IS_WRONG
                responseTime >= 150 -> MessageImportance.THIS_IS_ODD
                else -> MessageImportance.THIS_IS_NORMAL
            },
            responseTime
        )
    }

    companion object {
        private val statementInputRegex = Regex("(^|[^:])[?:]([a-zA-Z0-9_]+)")
    }
}

class JdbcUpdateCursor(private val update: Int) : ResultCursor {
    private var didRead = false
    override suspend fun getInt(column: Int): Int? = update
    override suspend fun getLong(column: Int): Long? = update.toLong()

    override suspend fun getString(column: Int): String? = error("Invalid type trying to read update size")
    override suspend fun getBoolean(column: Int): Boolean? = error("Invalid type trying to read update size")
    override suspend fun getDouble(column: Int): Double? = error("Invalid type trying to read update size")

    override suspend fun next(): Boolean {
        if (!didRead) {
            didRead = true
            return true
        }
        return false
    }

}

class JdbcResultCursor(private val rs: ResultSet) : ResultCursor {
    override suspend fun getInt(column: Int): Int? = rs.getInt(column + 1).takeIf { !rs.wasNull() }
    override suspend fun getLong(column: Int): Long? = rs.getLong(column + 1).takeIf { !rs.wasNull() }
    override suspend fun getString(column: Int): String? = rs.getString(column + 1).takeIf { !rs.wasNull() }
    override suspend fun getBoolean(column: Int): Boolean? = rs.getBoolean(column + 1).takeIf { !rs.wasNull() }
    override suspend fun getDouble(column: Int): Double? = rs.getDouble(column + 1).takeIf { !rs.wasNull() }
    override suspend fun next(): Boolean {
        return rs.next()
    }
}
