package dk.sdu.cloud.service.db

/**
 * Provides a fake [DBSessionFactory] all session objects are [Unit]
 *
 * Can be useful if existing code doesn't require DB sessions to be opened.
 */
object FakeDBSessionFactory : DBSessionFactory<Unit> {
    override suspend fun openSession() {}

    override suspend fun closeSession(session: Unit) {}

    override suspend fun openTransaction(session: Unit) {}

    override suspend fun commit(session: Unit) {}

    override suspend fun close() {}
}
