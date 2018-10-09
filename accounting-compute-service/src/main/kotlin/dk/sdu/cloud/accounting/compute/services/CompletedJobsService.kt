package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class CompletedJobsService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: CompletedJobsDao<DBSession>
) {
    fun insert(event: AccountingJobCompletedEvent): Unit = insertBatch(listOf(event))

    fun insertBatch(events: List<AccountingJobCompletedEvent>) {
        db.withTransaction { session ->
            events.forEach {
                dao.upsert(session, it)
            }
        }
    }

    fun listEvents(
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<AccountingJobCompletedEvent> {
        return db.withTransaction {
            dao.listEvents(it, paging, context, user)
        }
    }

    fun computeUsage(
        context: ContextQuery,
        user: String
    ): Long {
        return db.withTransaction {
            dao.computeUsage(it, context, user)
        }
    }
}

interface CompletedJobsDao<Session> {
    fun upsert(
        session: Session,
        event: AccountingJobCompletedEvent
    )

    fun listEvents(
        session: Session,
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String
    ): Page<AccountingJobCompletedEvent>

    fun computeUsage(
        session: Session,
        context: ContextQuery,
        user: String
    ): Long
}
