package dk.sdu.cloud.accounting.services.accounting

interface AccountingPersistence {
    suspend fun initialize()
    suspend fun flushChanges()
}

object FakeAccountingPersistence : AccountingPersistence {
    override suspend fun initialize() {

    }

    override suspend fun flushChanges() {

    }
}
