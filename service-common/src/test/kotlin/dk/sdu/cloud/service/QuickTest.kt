package dk.sdu.cloud.service

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

fun main(args: Array<String>) {
    DummyFactory.withTransaction {
        TODO()
    }
}

object DummyFactory : DBSessionFactory<Unit>, Loggable {
    override fun openSession() {
        log.info("openSession")
    }

    override fun closeSession(session: Unit) {
        log.info("closeSession")
    }

    override fun openTransaction(session: Unit) {
        log.info("openTransaction")
    }

    override fun commit(session: Unit) {
        log.info("commit")
    }

    override fun close() {
        log.info("close")
    }

    override val log = logger()
}
