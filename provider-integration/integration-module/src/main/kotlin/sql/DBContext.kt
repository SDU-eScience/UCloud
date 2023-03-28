package dk.sdu.cloud.sql

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debugSystem
import kotlinx.coroutines.delay
import org.postgresql.util.PSQLException

sealed class DBContext {
    abstract class ConnectionFactory() : DBContext() {
        abstract suspend fun openSession(): Connection
        abstract suspend fun close()
    }

    abstract class Connection : DBContext() {
        abstract suspend fun close()
        abstract suspend fun openTransaction()
        abstract suspend fun commit()
        abstract suspend fun isReady(): Boolean
        open suspend fun rollback() {}

        @Suppress("EmptyFunctionBlock")
        open fun flush() {
        }

        abstract fun prepareStatement(statement: String): PreparedStatement
    }

}

interface PreparedStatement {
    suspend fun bindNull(param: String)
    suspend fun bindInt(param: String, value: Int)
    suspend fun bindLong(param: String, value: Long)
    suspend fun bindString(param: String, value: String)
    suspend fun bindBoolean(param: String, value: Boolean)
    suspend fun bindDouble(param: String, value: Double)
    suspend fun bindList(param: String, value: List<Any?>)
    suspend fun execute(isUpdateHint: Boolean? = null): ResultCursor
    suspend fun reset()
    suspend fun close()
}

suspend fun PreparedStatement.bindIntNullable(param: String, value: Int?) {
    if (value == null) bindNull(param)
    else bindInt(param, value)
}

suspend fun PreparedStatement.bindLongNullable(param: String, value: Long?) {
    if (value == null) bindNull(param)
    else bindLong(param, value)
}

suspend fun PreparedStatement.bindStringNullable(param: String, value: String?) {
    if (value == null) bindNull(param)
    else bindString(param, value)
}

suspend fun PreparedStatement.bindBooleanNullable(param: String, value: Boolean?) {
    if (value == null) bindNull(param)
    else bindBoolean(param, value)
}

suspend fun PreparedStatement.bindDoubleNullable(param: String, value: Double?) {
    if (value == null) bindNull(param)
    else bindDouble(param, value)
}

suspend inline fun PreparedStatement.use(block: (statement: PreparedStatement) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

suspend inline fun PreparedStatement.useAndInvokeAndDiscard(
    prepare: PreparedStatement.() -> Unit = {},
) {
    use {
        invokeAndDiscard(prepare)
    }
}

suspend inline fun PreparedStatement.useAndInvoke(
    prepare: PreparedStatement.() -> Unit = {},
    readRow: (ResultCursor) -> Unit
) {
    use {
        invoke(prepare, readRow)
    }
}

suspend inline fun PreparedStatement.invokeAndDiscard(
    prepare: PreparedStatement.() -> Unit = {},
) {
    invoke(prepare) { }
}

suspend inline fun PreparedStatement.invoke(
    prepare: PreparedStatement.() -> Unit = {},
    readRow: (ResultCursor) -> Unit
) {
    reset()
    prepare()
    val cursor = execute()
    while (cursor.next()) {
        readRow(cursor)
    }
}

interface ResultCursor {
    suspend fun getInt(column: Int): Int?
    suspend fun getLong(column: Int): Long?
    suspend fun getString(column: Int): String?
    suspend fun getBoolean(column: Int): Boolean?
    suspend fun getDouble(column: Int): Double?
    suspend fun next(): Boolean
}

suspend fun ResultCursor.discardResult() {
    while (next()) {
        // Do nothing
    }
}

suspend fun <R> DBContext.withSession(block: suspend (session: DBContext.Connection) -> R): R {
    for (attempt in 0 until 3) {
        try {
            return when (this) {
                is DBContext.ConnectionFactory -> {
                    val session = openSession()
                    try {
                        session.withTransaction { _ ->
                            block(session)
                        }
                    } finally {
                        session.close()
                    }
                }

                is DBContext.Connection -> {
                    block(this)
                }
            }
        } catch (ex: PSQLException) {
            // NOTE(Dan): These automatic retries are required for the integration test suite and are generally useful
            // for production code.
            if (ex.message?.contains("This connection has been closed") == true) {
                delay(500)
            } else {
                throw ex
            }
        }
    }

    throw RPCException("Database is unavailable", HttpStatusCode.InternalServerError)
}

suspend fun <R> DBContext.Connection.withTransaction(
    autoCommit: Boolean = true,
    autoFlush: Boolean = false,
    closure: suspend (DBContext.Connection) -> R
): R {
    return debugSystem.useContext(DebugContextType.DATABASE_TRANSACTION) {
        openTransaction()
        try {
            val result = closure(this)
            if (autoFlush) flush()
            if (autoCommit) commit()
            result
        } catch (ex: Throwable) {
            rollback()
            throw ex
        }
    }
}
