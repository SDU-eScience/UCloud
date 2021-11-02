package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

sealed class SqlObject {
    abstract suspend fun verify(
        session: suspend () -> AsyncDBConnection,
        close: suspend (AsyncDBConnection) -> Unit = {}
    ): String
    private val ref = AtomicReference<String?>(null)
    private val verificationMutex = Mutex()

    protected suspend fun baseVerify(
        session: suspend () -> AsyncDBConnection,
        close: suspend (AsyncDBConnection) -> Unit,
        capturedValue: String,
        verificationQuery: String,
        useReturnedValue: Boolean = false,
        params: (suspend EnhancedPreparedStatement.() -> Unit)? = null,
    ): String {
        val currentValue = ref.get()
        if (currentValue != null) return currentValue
        verificationMutex.withLock {
            val newValue = ref.get()
            if (newValue != null) return newValue
            val sess = session()
            try {
                val success = sess
                    .sendPreparedStatement(
                        {
                            setParameter("value", capturedValue)
                            params?.invoke(this)
                        },
                        verificationQuery,
                    )
                    .rows
                    .map { it.getString(0)!! }

                if (success.size != 1) {
                    throw IllegalStateException("$capturedValue is not valid for use!")
                }

                val returnValue = if (useReturnedValue) success[0] else capturedValue
                ref.set(returnValue)
                return returnValue
            } finally {
                close(sess)
            }
        }
    }

    class Table(
        private val value: String
    ) : SqlObject() {
        override suspend fun verify(
            session: suspend () -> AsyncDBConnection,
            close: suspend (AsyncDBConnection) -> Unit
        ): String {
            return baseVerify(session, close, value, "select :value::regclass")
        }
    }

    class Function(private val value: String) : SqlObject() {
        override suspend fun verify(
            session: suspend () -> AsyncDBConnection,
            close: suspend (AsyncDBConnection) -> Unit
        ): String {
            return baseVerify(session, close, value, "select :value::regproc")
        }
    }

    class Column(private val table: Table, private val column: String) : SqlObject() {
        override suspend fun verify(
            session: suspend () -> AsyncDBConnection,
            close: suspend (AsyncDBConnection) -> Unit
        ): String {
            return baseVerify(
                session,
                close,
                column,
                //language=sql
                """
                    select quote_ident(:value)
                    from pg_attribute
                    where
                        attrelid = :table::regclass and
                        attname = :value and
                        not attisdropped and -- exclude dropped columns
                        attnum > 0 -- exclude system columns
                """,
                true,
                {
                    setParameter("table", table.verify(session))
                }
            )
        }

        suspend fun verifyUnquoted(
            session: suspend () -> AsyncDBConnection,
            close: suspend (AsyncDBConnection) -> Unit
        ): String {
            verify(session, close)
            return column
        }
    }
}