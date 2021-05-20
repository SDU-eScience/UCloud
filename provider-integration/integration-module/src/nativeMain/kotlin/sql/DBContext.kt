package dk.sdu.cloud.sql

sealed class DBContext {
    abstract class ConnectionFactory() : DBContext() {
        abstract fun openSession(): Connection
        abstract fun close()
    }

    abstract class Connection : DBContext() {
        abstract fun close()
        abstract fun openTransaction()
        abstract fun commit()
        open fun rollback() {}
        @Suppress("EmptyFunctionBlock")
        open fun flush() {}
        abstract fun prepareStatement(statement: String): PreparedStatement
    }

}

interface PreparedStatement {
    fun bindNull(param: String)
    fun bindInt(param: String, value: Int)
    fun bindLong(param: String, value: Long)
    fun bindString(param: String, value: String)
    fun bindBoolean(param: String, value: Boolean)
    fun bindDouble(param: String, value: Double)
    fun execute(): ResultCursor
    fun reset()
    fun close()
}

inline fun PreparedStatement.use(block: (statement: PreparedStatement) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

inline fun PreparedStatement.useAndInvokeAndDiscard(
    prepare: PreparedStatement.() -> Unit = {},
) {
    use {
        invokeAndDiscard(prepare)
    }
}

inline fun PreparedStatement.useAndInvoke(
    prepare: PreparedStatement.() -> Unit = {},
    readRow: (ResultCursor) -> Unit
) {
    use {
        invoke(prepare, readRow)
    }
}

inline fun PreparedStatement.invokeAndDiscard(
    prepare: PreparedStatement.() -> Unit = {},
) {
    invoke(prepare) {  }
}

inline fun PreparedStatement.invoke(
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
    fun getInt(column: Int): Int?
    fun getLong(column: Int): Long?
    fun getString(column: Int): String?
    fun getBoolean(column: Int): Boolean?
    fun getDouble(column: Int): Double?
    fun next(): Boolean
}

fun ResultCursor.discardResult() {
    while (next()) {
        // Do nothing
    }
}

fun <R> DBContext.withSession(block: (session: DBContext.Connection) -> R): R {
    return when (this) {
        is DBContext.ConnectionFactory -> {
            val session = openSession()
            try {
                openSession().withTransaction { _ ->
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
}

fun <R> DBContext.Connection.withTransaction(
    autoCommit: Boolean = true,
    autoFlush: Boolean = false,
    closure: (DBContext.Connection) -> R
): R {
    openTransaction()
    try {
        val result = closure(this)
        if (autoFlush) flush()
        if (autoCommit) commit()
        return result
    } catch (ex: Throwable) {
        rollback()
        throw ex
    }
}
