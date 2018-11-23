package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.api.ContextQueryImpl
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import java.math.BigDecimal

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

    fun listAllEvents(
        context: ContextQuery,
        user: String
    ): List<AccountingJobCompletedEvent> {
        return db.withTransaction {
            dao.listAllEvents(it, context, user)
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

    fun computeBillableItems(
        since: Long,
        until: Long,
        user: String
    ): List<BillableItem> {
        val usageInMillis = db.withTransaction { dao.computeUsage(it, ContextQueryImpl(since, until, null), user) }
        // In this example we only bill the for an integer amount of minutes. The remainder is discarded.
        val billableUnits = usageInMillis / MINUTES_MS
        val pricePerUnit = BigDecimal("0.0001")
        val currencyName = Currencies.DKK

        return listOf(
            BillableItem("Compute time (minutes)", billableUnits, SerializedMoney(pricePerUnit, currencyName))
        )
    }
}

interface CompletedJobsDao<Session> {
    fun upsert(
        session: Session,
        event: AccountingJobCompletedEvent
    )

    fun listAllEvents(
        session: Session,
        context: ContextQuery,
        user: String
    ): List<AccountingJobCompletedEvent>

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
