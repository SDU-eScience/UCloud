package dk.sdu.cloud.sql

import kotlinx.cinterop.*
import platform.posix.memcpy
import sqlite3.*

class Sqlite3Driver(
    private val file: String,
) : DBContext.ConnectionFactory() {
    override fun openSession(): Connection {
        val connPtr = nativeHeap.alloc<CPointerVar<sqlite3>>()
        sqlite3_open_v2(
            file,
            connPtr.ptr,
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX or SQLITE_OPEN_URI,
            null
        ).unwrapSqlite3Error(connPtr)

        return Sqlite3Connection(connPtr)
    }

    override fun close() {
    }
}

private fun Int.unwrapSqlite3Error(sqlite: CPointerVar<sqlite3>) {
    if (this != SQLITE_OK) {
        throw Sqlite3Exception(sqlite3_errmsg(sqlite.value)?.toKString() + "($this)")
    }
}

class Sqlite3Connection(
    private val connPtr: CPointerVar<sqlite3>,
) : DBContext.Connection() {
    private val beginStatement = prepareStatement("begin")
    private val rollbackStatement = prepareStatement("rollback")
    private val commitStatement = prepareStatement("commit")

    override fun close() {
        beginStatement.close()
        rollbackStatement.close()
        commitStatement.close()
        sqlite3_close(connPtr.value).unwrapSqlite3Error(connPtr)
        nativeHeap.free(connPtr)
    }

    override fun prepareStatement(statement: String): PreparedStatement {
        val statementPtr = nativeHeap.alloc<CPointerVar<sqlite3_stmt>>()
        try {
            sqlite3_prepare(connPtr.value, statement, -1, statementPtr.ptr, null).unwrapSqlite3Error(connPtr)
            return Sqlite3PreparedStatement(connPtr, statementPtr)
        } catch(ex: Throwable) {
            nativeHeap.free(statementPtr)
            throw ex
        }
    }

    override fun openTransaction() {
        beginStatement.reset()
        beginStatement.execute().discardResult()
    }

    override fun commit() {
        commitStatement.reset()
        commitStatement.execute().discardResult()
    }

    override fun rollback() {
        runCatching {
            rollbackStatement.reset()
            rollbackStatement.execute().discardResult()
        }
    }
}

class Sqlite3PreparedStatement(
    private val connPtr: CPointerVar<sqlite3>,
    private val statementPtr: CPointerVar<sqlite3_stmt>
) : PreparedStatement {
    private var didReset = true
    private val resultCursor = Sqlite3ResultCursor(connPtr, statementPtr)
    private val localCache = HashMap<String, Int>()
    private fun readParamIndex(param: String): Int {
        val cached = localCache[param]
        if (cached != null) return cached

        val idx = sqlite3_bind_parameter_index(statementPtr.value, ":$param")
        if (idx <= 0) throw Sqlite3Exception("Unknown parameter: $param")
        localCache[param] = idx
        return idx
    }

    override fun bindNull(param: String) {
        sqlite3_bind_null(statementPtr.value, readParamIndex(param)).unwrapSqlite3Error(connPtr)
    }

    override fun bindInt(param: String, value: Int) {
        sqlite3_bind_int(statementPtr.value, readParamIndex(param), value).unwrapSqlite3Error(connPtr)
    }

    override fun bindLong(param: String, value: Long) {
        sqlite3_bind_int64(statementPtr.value, readParamIndex(param), value).unwrapSqlite3Error(connPtr)
    }

    override fun bindString(param: String, value: String) {
        val encodedData = value.encodeToByteArray()
        val dataSize = encodedData.size.toULong()
        val allocatedData = platform.posix.malloc(dataSize)
        encodedData.usePinned { pinned ->
            memcpy(allocatedData, pinned.addressOf(0), dataSize)
        }

        sqlite3_bind_text_kt_fix(
            statementPtr.value,
            readParamIndex(param),
            allocatedData,
            dataSize.toInt(),
            staticCFunction { ptr ->
                platform.posix.free(ptr)
            }
        ).unwrapSqlite3Error(connPtr)
    }

    override fun bindBoolean(param: String, value: Boolean) {
        bindInt(param, if (value) 1 else 0)
    }

    override fun bindDouble(param: String, value: Double) {
        sqlite3_bind_double(statementPtr.value, readParamIndex(param), value).unwrapSqlite3Error(connPtr)
    }

    override fun reset() {
        sqlite3_reset(statementPtr.value).unwrapSqlite3Error(connPtr)
        sqlite3_clear_bindings(statementPtr.value).unwrapSqlite3Error(connPtr)
        didReset = true
    }

    override fun close() {
        sqlite3_finalize(statementPtr.value).unwrapSqlite3Error(connPtr)
        nativeHeap.free(statementPtr)
    }

    override fun execute(): ResultCursor {
        if (resultCursor.isCollectingResults) {
            throw Sqlite3Exception("Current result cursor is still open. Please discardResult()s if they are " +
                "not needed")
        }
        if (!didReset) throw Sqlite3Exception("PreparedStatements must be reset() before calling execute()")
        didReset = false
        resultCursor.isCollectingResults = true
        return resultCursor
    }
}

class Sqlite3ResultCursor(
    private val connPtr: CPointerVar<sqlite3>,
    private val statementPtr: CPointerVar<sqlite3_stmt>,
) : ResultCursor {
    var isCollectingResults = false

    override fun getInt(column: Int): Int? {
        val colType = sqlite3_column_type(statementPtr.value, column)
        if (colType == SQLITE_NULL) return null
        else if (colType != SQLITE_INTEGER) throw Sqlite3Exception("$column is not a valid integer")
        return sqlite3_column_int(statementPtr.value, column)
    }

    override fun getLong(column: Int): Long? {
        val colType = sqlite3_column_type(statementPtr.value, column)
        if (colType == SQLITE_NULL) return null
        else if (colType != SQLITE_INTEGER) throw Sqlite3Exception("$column is not a valid integer")
        return sqlite3_column_int64(statementPtr.value, column)
    }

    override fun getString(column: Int): String? {
        val colType = sqlite3_column_type(statementPtr.value, column)
        if (colType == SQLITE_NULL) return null
        else if (colType != SQLITE_TEXT) throw Sqlite3Exception("$column is not valid text")
        val data = sqlite3_column_text(statementPtr.value, column)
        val bytes = sqlite3_column_bytes(statementPtr.value, column)
        return data?.readBytes(bytes)?.toKString() ?:
            throw Sqlite3Exception("Could not decode sqlite3 string from column $column")
    }

    override fun getBoolean(column: Int): Boolean? {
        val colType = sqlite3_column_type(statementPtr.value, column)
        if (colType == SQLITE_NULL) return null
        else if (colType != SQLITE_INTEGER) throw Sqlite3Exception("$column is not a valid boolean")
        return sqlite3_column_int(statementPtr.value, column) == 1
    }

    override fun getDouble(column: Int): Double? {
        val colType = sqlite3_column_type(statementPtr.value, column)
        if (colType == SQLITE_NULL) return null
        else if (colType != SQLITE_FLOAT) throw Sqlite3Exception("$column is not a valid double")
        return sqlite3_column_double(statementPtr.value, column)
    }

    override fun next(): Boolean {
        val resultCode = sqlite3_step(statementPtr.value)
        if (resultCode == SQLITE_DONE) {
            isCollectingResults = false
            return false
        }

        if (resultCode == SQLITE_ROW) {
            return true
        }

        resultCode.unwrapSqlite3Error(connPtr)
        throw Sqlite3Exception("This should not happen. OK status code was treated as an error.")
    }
}

class Sqlite3Exception(message: String) : RuntimeException(message)
