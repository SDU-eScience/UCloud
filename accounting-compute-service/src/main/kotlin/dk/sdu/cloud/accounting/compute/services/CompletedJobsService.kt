package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.ContextQuery
import dk.sdu.cloud.accounting.api.ContextQueryImpl
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal

class CompletedJobsService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: CompletedJobsDao<DBSession>,
    private val serviceCloud: AuthenticatedClient
) {
    /**
     * Inserts a single event. Assumes input to be normalized, see [normalizeUsername].
     */
    fun insert(event: AccountingJobCompletedEvent): Unit = insertBatch(listOf(event))

    /**
     * Inserts batch events. Assumes input to be normalized, see [normalizeUsername].
     */
    fun insertBatch(events: List<AccountingJobCompletedEvent>) {
        db.withTransaction { session ->
            events.forEach {
                dao.upsert(session, it)
            }
        }
    }

    suspend fun listAllEvents(
        context: ContextQuery,
        user: String,
        role: Role?
    ): List<AccountingJobCompletedEvent> {
        val normalizedUser = normalizeUserInput(user, role)
        return db.withTransaction {
            dao.listAllEvents(it, context, normalizedUser)
        }
    }

    suspend fun listEvents(
        paging: NormalizedPaginationRequest,
        context: ContextQuery,
        user: String,
        role: Role?
    ): Page<AccountingJobCompletedEvent> {
        val normalizedUser = normalizeUserInput(user, role)
        return db.withTransaction {
            dao.listEvents(it, paging, context, normalizedUser)
        }
    }

    suspend fun computeUsage(
        context: ContextQuery,
        user: String,
        role: Role?
    ): Long {
        val normalizedUser = normalizeUserInput(user, role)
        return db.withTransaction {
            dao.computeUsage(it, context, normalizedUser)
        }
    }

    suspend fun computeBillableItems(
        since: Long,
        until: Long,
        user: String,
        role: Role?
    ): List<BillableItem> {
        val normalizedUser = normalizeUserInput(user, role)

        val usageInMillis =
            db.withTransaction { dao.computeUsage(it, ContextQueryImpl(since, until, null), normalizedUser) }
        // In this example we only bill the for an integer amount of minutes. The remainder is discarded.
        val billableUnits = usageInMillis / MINUTES_MS
        val pricePerUnit = BigDecimal("0.0001")
        val currencyName = Currencies.DKK

        return listOf(
            BillableItem("Compute time (minutes)", billableUnits, SerializedMoney(pricePerUnit, currencyName))
        )
    }

    private suspend fun normalizeUserInput(user: String, role: Role?): String {
        if (role != null) return normalizeUsername(user, role)
        val actualRole = UserDescriptions.lookupUsers
            .call(LookupUsersRequest(listOf(user)), serviceCloud)
            .orRethrowAs {
                log.warn("Caught an exception while normalizing user: ${it.error} ${it.statusCode}")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
            .results[user]?.role
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized).also {
                log.info("User does not exist: $user")
            }

        return normalizeUsername(user, actualRole)
    }

    companion object : Loggable {
        override val log = logger()
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
