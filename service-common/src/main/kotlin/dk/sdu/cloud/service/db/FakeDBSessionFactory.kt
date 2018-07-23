package dk.sdu.cloud.service.db

/**
 * Provides a fake [DBSessionFactory] all session objects are [Unit]
 *
 * Can be useful if existing code doesn't require DB sessions to be opened.
 */
object FakeDBSessionFactory : DBSessionFactory<Unit> {
    override fun openSession() {}

    override fun closeSession(session: Unit) {}

    override fun openTransaction(session: Unit) {}

    override fun commit(session: Unit) {}

    override fun close() {}
}