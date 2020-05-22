package dk.sdu.cloud.service.db

interface DBSessionFactory<Session> {
    suspend fun openSession(): Session
    suspend fun closeSession(session: Session)

    suspend fun openTransaction(session: Session)

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
    closure: suspend (Session) -> R
): R {
    openTransaction(session)
    try {
        val result = closure(session)
        if (autoFlush) flush(session)
        if (autoCommit) commit(session)
        return result
    } catch (ex: Throwable) {
        rollback(session)
        throw ex
    }
}

suspend fun <R, Session> DBSessionFactory<Session>.withTransaction(
    autoCommit: Boolean = true,
    autoFlush: Boolean = false,
    closure: suspend (Session) -> R
): R {
    return usingSession { session ->
        withTransaction(session, autoCommit, autoFlush) {
            closure(it)
        }
    }
}


