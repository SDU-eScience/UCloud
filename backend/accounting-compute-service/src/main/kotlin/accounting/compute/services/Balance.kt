package dk.sdu.cloud.accounting.compute.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.accounting.compute.api.AccountType
import dk.sdu.cloud.accounting.compute.api.CreditsAccount
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object BalanceTable : SQLTable("balance") {
    val accountId = text("account_id")
    val accountType = text("account_type")
    val accountMachineType = text("account_machine_type")
    val balance = long("balance")
}

object TransactionTable : SQLTable("transactions") {
    val accountId = text("account_id")
    val accountType = text("account_type")
    val accountMachineType = text("account_machine_type")
    val amount = long("amount")
    val reservationId = text("reservation_id")
    val isReserved = bool("is_reserved")
    val initiatedBy = text("initiated_by")
    val completedAt = timestamp("completed_at")
    val expiresAt = timestamp("expires_at")
}

object GrantAdminTable : SQLTable("grant_administrators") {
    val username = text("username")
}

class BalanceService(
    private val projectCache: ProjectCache
) {
    suspend fun requirePermissionToReadBalance(
        ctx: DBContext,
        initiatedBy: String,
        accountId: String,
        accountType: AccountType
    ) {
        if (accountType == AccountType.USER && initiatedBy == accountId) return
        if (isGrantAdministrator(ctx, initiatedBy)) return
        if (accountType == AccountType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy)
            val project = memberStatus?.membership?.find { it.projectId == accountId }
            if (project != null && project.whoami.role.isAdmin()) {
                return
            }
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun getBalance(
        ctx: DBContext,
        initiatedBy: String,
        account: CreditsAccount
    ): Long {
        return ctx.withSession { session ->
            requirePermissionToReadBalance(session, initiatedBy, account.id, account.type)

            session
                .sendPreparedStatement(
                    {
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("accountMachineType", account.machineType.name)
                    },

                    """
                        select balance 
                        from balance 
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and
                            account_machine_type = ?accountMachineType
                    """
                )
                .rows
                .firstOrNull()
                ?.let { it.getLong(0)!! }
                ?: throw RPCException("Account not found", HttpStatusCode.NotFound)
        }
    }

    suspend fun setBalance(
        ctx: DBContext,
        initiatedBy: String,
        account: CreditsAccount,
        lastKnownBalance: Long,
        amount: Long
    ) {
        ctx.withSession { session ->
            if (!isGrantAdministrator(ctx, initiatedBy)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            try {
                val currentBalance = getBalance(session, initiatedBy, account)
                if (currentBalance != lastKnownBalance) {
                    throw RPCException("Balance has been updated since you last viewed it!", HttpStatusCode.Conflict)
                }
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    if (lastKnownBalance != 0L) {
                        throw RPCException("Balance has been updated since you last viewed it!", HttpStatusCode.Conflict)
                    }

                    // TODO Verify account exists
                    session.insert(BalanceTable) {
                        set(BalanceTable.accountId, account.id)
                        set(BalanceTable.accountType, account.type.name)
                        set(BalanceTable.accountMachineType, account.machineType.name)
                        set(BalanceTable.balance, amount)
                    }

                    return@withSession
                }

                throw ex
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("accountMachineType", account.machineType.name)
                    },

                    """
                        update balance
                        set balance = ?amount
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and 
                            account_machine_type = ?accountMachineType
                    """
                )
        }
    }

    suspend fun addToBalance(
        ctx: DBContext,
        initiatedBy: String,
        account: CreditsAccount,
        amount: Long
    ) {
        ctx.withSession { session ->
            if (!isGrantAdministrator(ctx, initiatedBy)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            val rowsAffected = session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("accountMachineType", account.machineType.name)
                    },

                    """
                        update balance  
                        set balance = balance + ?amount
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and
                            account_machine_type = ?accountMachineType
                    """
                )
                .rowsAffected

            if (rowsAffected < 1) throw RPCException("Account not found", HttpStatusCode.NotFound)
        }
    }

    suspend fun getReservedCredits(
        ctx: DBContext,
        account: CreditsAccount
    ): Long {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("accountId", account.id)
                setParameter("accountType", account.type.name)
                setParameter("accountMachineType", account.machineType.name)
            }

            session
                .sendPreparedStatement(
                    params,

                    """
                        delete from transactions 
                        where
                            account_id = ?accountId and
                            account_type = ?accountType and
                            account_machine_type = ?accountMachineType and
                            is_reserved = true and
                            expires_at is not null and
                            expires_at < timezone('utc', now())
                    """
                )

            session
                .sendPreparedStatement(
                    params,

                    """
                        select sum(amount)
                        from transactions
                        where
                            account_id = ?accountId and
                            account_type = ?accountType and
                            account_machine_type = ?accountMachineType and
                            is_reserved = true
                    """
                )
                .rows
                .firstOrNull()
                ?.let { it.getLong(0)!! }
                ?: 0L
        }
    }

    suspend fun reserveCredits(
        ctx: DBContext,
        initiatedBy: String,
        account: CreditsAccount,
        reservationId: String,
        amount: Long,
        expiresAt: Long
    ) {
        ctx.withSession { session ->
            val balance = getBalance(ctx, initiatedBy, account)
            val reserved = getReservedCredits(ctx, account)
            if (reserved + amount > balance) {
                throw RPCException("Insufficient funds", HttpStatusCode.PaymentRequired)
            }

            session.insert(TransactionTable) {
                set(TransactionTable.accountId, account.id)
                set(TransactionTable.accountType, account.type.name)
                set(TransactionTable.accountMachineType, account.machineType.name)
                set(TransactionTable.amount, amount)
                set(TransactionTable.expiresAt, LocalDateTime(expiresAt, DateTimeZone.UTC))
                set(TransactionTable.initiatedBy, initiatedBy)
                set(TransactionTable.isReserved, true)
                set(TransactionTable.completedAt, LocalDateTime.now(DateTimeZone.UTC))
                set(TransactionTable.reservationId, reservationId)
            }
        }
    }

    suspend fun chargeFromReservation(
        ctx: DBContext,
        reservationId: String,
        amount: Long
    ) {
        ctx.withSession { session ->
            val transaction = session
                .sendPreparedStatement(
                    {
                        setParameter("reservationId", reservationId)
                    },
                    """
                        select * from transactions 
                        where
                            reservation_id = ?reservationId and
                            is_reserved = true and
                            expires_at is not null and
                            expires_at > timezone('utc', now())
                    """
                )
                .rows
                .singleOrNull()
                ?: throw RPCException("No such reservation", HttpStatusCode.NotFound)

            val accountId = transaction.getField(TransactionTable.accountId)
            val accountType = transaction.getField(TransactionTable.accountType)
            val machineType = transaction.getField(TransactionTable.accountMachineType)
            val initiatedBy = transaction.getField(TransactionTable.initiatedBy)

            session
                .sendPreparedStatement(
                    { setParameter("reservationId", reservationId) },
                    "delete from transactions where reservation_id = ?reservationId and is_reserved = true"
                )

            session.insert(TransactionTable) {
                set(TransactionTable.accountId, accountId)
                set(TransactionTable.accountType, accountType)
                set(TransactionTable.accountType, machineType)
                set(TransactionTable.amount, amount)
                set(TransactionTable.expiresAt, null)
                set(TransactionTable.initiatedBy, initiatedBy)
                set(TransactionTable.isReserved, false)
                set(TransactionTable.completedAt, LocalDateTime.now(DateTimeZone.UTC))
                set(TransactionTable.reservationId, reservationId)
            }
        }
    }

    suspend fun addGrantAdministrator(
        ctx: DBContext,
        username: String
    ) {
        try {
            ctx.withSession { session ->
                session.insert(GrantAdminTable) {
                    set(GrantAdminTable.username, username)
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException("Already an administrator", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    private suspend fun isGrantAdministrator(
        ctx: DBContext,
        username: String
    ): Boolean {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },

                    """
                        select count(*)
                        from grant_administrators     
                        where username = ?username
                    """
                )
                .rows
                .first()
                .let { it.getLong(0)!! } > 0L
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}