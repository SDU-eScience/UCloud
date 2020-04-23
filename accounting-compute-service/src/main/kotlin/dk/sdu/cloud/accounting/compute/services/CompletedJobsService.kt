package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction

class CompletedJobsService(
    private val db: DBSessionFactory<HibernateSession>,
    private val dao: CompletedJobsDao
) {
    /**
     * Inserts a single event. Assumes input to be normalized, see [normalizeUsername].
     */
    suspend fun insert(event: AccountingJobCompletedEvent): Unit = insertBatch(listOf(event))

    /**
     * Inserts batch events. Assumes input to be normalized, see [normalizeUsername].
     */
    suspend fun insertBatch(events: List<AccountingJobCompletedEvent>) {
        db.withTransaction { session ->
            events.forEach {
                dao.upsert(session, it)
            }
        }
    }

    suspend fun computeUsage(user: ComputeUser): Long {
        return db.withTransaction {
            dao.computeUsage(it, user)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
