package dk.sdu.cloud.service.db

import java.io.Closeable

interface DBSessionFactory<Session> : Closeable {
    fun <R> withSession(closure: (Session) -> R): R
    fun <R> withTransaction(session: Session, autoCommit: Boolean = true, closure: (Session) -> R): R
    fun commit(session: Session)
}

fun <R, Session> DBSessionFactory<Session>.withTransaction(autoCommit: Boolean = true, closure: (Session) -> R): R {
    return withSession {
        withTransaction(it, autoCommit) {
            closure(it)
        }
    }
}
