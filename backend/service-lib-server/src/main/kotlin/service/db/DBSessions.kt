package dk.sdu.cloud.service.db

import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.TransactionMode

interface DBSessionFactory<Session> {
    suspend fun openSession(): Session
    suspend fun closeSession(session: Session)

    suspend fun openTransaction(session: Session, transactionMode: TransactionMode? = null)

    suspend fun commit(session: Session)
    suspend fun close()

    suspend fun rollback(session: Session) {}

    @Suppress("EmptyFunctionBlock")
    suspend fun flush(session: Session) {}
}

suspend inline fun <R, Session> DBSessionFactory<Session>.usingSession(closure: (Session) -> R): R {
    val session = openSession()
    return try {
        closure(session)
    } finally {
        closeSession(session)
    }
}

suspend fun <R, Session> DBSessionFactory<Session>.withTransaction(
    session: Session,
    autoCommit: Boolean = true,
    autoFlush: Boolean = false,
    transactionMode: TransactionMode? = null,
    closure: suspend (Session) -> R
): R {
    require(this is AsyncDBSessionFactory)
    return this.debug.system.useContext(DebugContextType.DATABASE_TRANSACTION) {
        val start = Time.now()
        openTransaction(session, transactionMode)
        try {
            val result = closure(session)
            if (autoFlush) flush(session)
            if (autoCommit) commit(session)
            result
        } catch (ex: Throwable) {
            rollback(session)
            throw ex
        } finally {
            AsyncDBSessionFactory.transactionDuration.observe((Time.now() - start).toDouble())
        }
    }
}

suspend fun <R, Session> DBSessionFactory<Session>.withTransaction(
    autoCommit: Boolean = true,
    autoFlush: Boolean = false,
    transactionMode: TransactionMode? = null,
    closure: suspend (Session) -> R
): R {
    return usingSession { session ->
        withTransaction(session, autoCommit, autoFlush, transactionMode) {
            closure(it)
        }
    }
}


