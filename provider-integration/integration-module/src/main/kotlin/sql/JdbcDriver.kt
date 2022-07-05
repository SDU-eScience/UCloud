package dk.sdu.cloud.sql

import io.ktor.utils.io.pool.*
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteException
import java.sql.Connection as JavaSqlConnection
import java.sql.PreparedStatement as JavaSqlPreparedStatement
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

class JdbcDriver(
    private val file: String
) : DBContext.ConnectionFactory() {
    override fun openSession(): JdbcConnection {
        return connectionPool.borrow()
    }

    override fun close() {

    }

    private val connectionPool = object : DefaultPool<JdbcConnection>(8) {
        override fun produceInstance(): JdbcConnection {
            return JdbcConnection(
                DriverManager.getConnection("jdbc:sqlite:$file", SQLiteConfig().apply {
                    setJournalMode(SQLiteConfig.JournalMode.WAL)
                }.toProperties()),
                this
            )
        }
    }
}


class JdbcConnection(
    private val connection: JavaSqlConnection,
    private val pool: ObjectPool<JdbcConnection>,
) : DBContext.Connection() {
    init {
        connection.autoCommit = false
    }

    override suspend fun close() {
        pool.recycle(this)
    }

    override suspend fun openTransaction() {
        // Already open
    }

    override suspend fun commit() {
        connection.commit()
    }

    override suspend fun rollback() {
        connection.rollback()
    }

    override fun prepareStatement(statement: String): PreparedStatement =
        JdbcPreparedStatement(statement, connection)
}

class JdbcPreparedStatement(
    private val rawStatement: String,
    private val conn: JavaSqlConnection,
) : PreparedStatement {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val boundValues = HashSet<String>()
    private val preparedStatement: String
    private val parameters: Array<Any?>
    private val rawParameters = HashMap<String, Any?>()
    private val statement: JavaSqlPreparedStatement

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
        } catch (ex: SQLiteException) {
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
            })
        }
    }

    private fun indices(name: String): List<Int> = parameterNamesToIndex[name] ?: error("Unknown parameter $name")

    override suspend fun bindNull(param: String) {
        for (idx in indices(param)) {
            statement.setNull(idx + 1, 0)
        }
    }

    override suspend fun bindInt(param: String, value: Int) {
        for (idx in indices(param)) {
            statement.setInt(idx + 1, value)
        }
    }

    override suspend fun bindLong(param: String, value: Long) {
        for (idx in indices(param)) {
            statement.setLong(idx + 1, value)
        }
    }

    override suspend fun bindString(param: String, value: String) {
        for (idx in indices(param)) {
            statement.setString(idx + 1, value)
        }
    }

    override suspend fun bindBoolean(param: String, value: Boolean) {
        for (idx in indices(param)) {
            statement.setBoolean(idx + 1, value)
        }
    }

    override suspend fun bindDouble(param: String, value: Double) {
        for (idx in indices(param)) {
            statement.setDouble(idx + 1, value)
        }
    }

    private fun willReturnResults(): Boolean {
        return try {
            statement.metaData.columnCount > 0
        } catch (ex: SQLException) {
            false
        }
    }

    override suspend fun execute(): ResultCursor {
        return if (!willReturnResults()) {
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
