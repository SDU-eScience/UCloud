package dk.sdu.cloud.service.db

import java.io.Closeable

interface DBSessionFactory<Session> : Closeable {
    fun openSession(): Session
    fun closeSession(session: Session)

    fun openTransaction(session: Session)

    fun commit(session: Session)
}

inline fun <R, Session> DBSessionFactory<Session>.withSession(closure: (Session) -> R): R {
    val session = openSession()
    return try {
        closure(session)
    } finally {
        closeSession(session)
    }
}

inline fun <R, Session> DBSessionFactory<Session>.withTransaction(
    session: Session,
    autoCommit: Boolean = true,
    closure: (Session) -> R
): R {
    openTransaction(session)
    try {
        val result = closure(session)
        if (autoCommit) commit(session)
        return result
    } finally {
    }
}

inline fun <R, Session> DBSessionFactory<Session>.withTransaction(autoCommit: Boolean = true, closure: (Session) -> R): R {
    return withSession {
        withTransaction(it, autoCommit) {
            closure(it)
        }
    }
}
