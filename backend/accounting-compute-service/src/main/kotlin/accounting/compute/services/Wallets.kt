package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.compute.api.ProductCategoryId
import dk.sdu.cloud.accounting.compute.api.WalletOwnerType
import dk.sdu.cloud.accounting.compute.api.Wallet
import dk.sdu.cloud.accounting.compute.api.WalletBalance
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object WalletTable : SQLTable("wallets") {
    val accountId = text("account_id", notNull = true)
    val accountType = text("account_type", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)

    val balance = long("balance", notNull = true)
}

object TransactionTable : SQLTable("transactions") {
    val accountId = text("account_id", notNull = true)
    val accountType = text("account_type", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)

    val id = text("id")

    val productId = text("product_id")
    val units = long("units")
    val amount = long("amount")
    val isReserved = bool("is_reserved")
    val initiatedBy = text("initiated_by")
    val completedAt = timestamp("completed_at")
    val expiresAt = timestamp("expires_at")
}

class BalanceService(
    private val projectCache: ProjectCache,
    private val verificationService: VerificationService
) {
    suspend fun requirePermissionToReadBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        walletOwnerType: WalletOwnerType
    ) {
        if (initiatedBy == Actor.System) return
        if (initiatedBy is Actor.User && initiatedBy.principal.role in Roles.PRIVILEDGED) return

        if (walletOwnerType == WalletOwnerType.USER && initiatedBy.username == accountId) return
        if (walletOwnerType == WalletOwnerType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy.username)
            val project = memberStatus?.membership?.find { it.projectId == accountId }
            if (project != null && project.whoami.role.isAdmin()) {
                return
            }
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun requirePermissionToWriteBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        walletOwnerType: WalletOwnerType
    ) {
        if (initiatedBy == Actor.System) return
        if (initiatedBy is Actor.User && initiatedBy.principal.role in Roles.PRIVILEDGED) return

        if (walletOwnerType == WalletOwnerType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy.username)
            val project = memberStatus?.membership?.find { it.parentId == accountId }
            if (project != null && project.whoami.role.isAdmin()) {
                return
            }
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun getWalletsForAccount(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        accountOwnerType: WalletOwnerType
    ): List<WalletBalance> {
        return ctx.withSession { session ->
            requirePermissionToWriteBalance(session, initiatedBy, accountId, accountOwnerType)
            verificationService.verify(accountId, accountOwnerType)

            session
                .sendPreparedStatement(
                    {
                        setParameter("accountId", accountId)
                        setParameter("accountType", accountOwnerType.name)
                    },

                    """
                        select *
                        from wallets 
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType
                    """
                )
                .rows
                .map {
                    WalletBalance(
                        ProductCategoryId(
                            it.getField(WalletTable.productProvider),
                            it.getField(WalletTable.productCategory)
                        ),
                        it.getField(WalletTable.balance)
                    )
                }
        }
    }

    suspend fun getBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        account: Wallet,
        verify: Boolean = true
    ): Pair<Long, Boolean> {
        return ctx.withSession { session ->
            requirePermissionToReadBalance(session, initiatedBy, account.id, account.type)

            session
                .sendPreparedStatement(
                    {
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("productCategory", account.paysFor.id)
                        setParameter("productProvider", account.paysFor.provider)
                    },

                    """
                        select balance 
                        from wallets 
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and
                            product_category = ?productCategory and
                            product_provider = ?productProvider
                    """
                )
                .rows
                .firstOrNull()
                ?.let { Pair(it.getLong(0)!!, true) }
                ?: run {
                    if (verify) {
                        verificationService.verify(account.id, account.type)
                    }
                    Pair(0L, false)
                }
        }
    }

    suspend fun setBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        account: Wallet,
        lastKnownBalance: Long,
        amount: Long
    ) {
        ctx.withSession { session ->
            requirePermissionToWriteBalance(session, initiatedBy, account.id, account.type)
            val (currentBalance, exists) = getBalance(session, initiatedBy, account, true)
            if (currentBalance != lastKnownBalance) {
                throw RPCException("Balance has been updated since you last viewed it!", HttpStatusCode.Conflict)
            }

            if (!exists) {
                if (lastKnownBalance != 0L) {
                    throw RPCException(
                        "Balance has been updated since you last viewed it!",
                        HttpStatusCode.Conflict
                    )
                }

                // TODO Verify account exists
                session.insert(WalletTable) {
                    set(WalletTable.accountId, account.id)
                    set(WalletTable.accountType, account.type.name)
                    set(WalletTable.productCategory, account.paysFor.id)
                    set(WalletTable.productProvider, account.paysFor.provider)
                    set(WalletTable.balance, amount)
                }

                return@withSession
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("productCategory", account.paysFor.id)
                        setParameter("productProvider", account.paysFor.provider)
                    },

                    """
                        update wallets
                        set balance = ?amount
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and 
                            product_category = ?productCategory and
                            product_provider = ?productProvider
                    """
                )
        }
    }

    suspend fun addToBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        account: Wallet,
        amount: Long
    ) {
        ctx.withSession { session ->
            requirePermissionToWriteBalance(session, initiatedBy, account.id, account.type)
            val rowsAffected = session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("accountId", account.id)
                        setParameter("accountType", account.type.name)
                        setParameter("productCategory", account.paysFor.id)
                        setParameter("productProvider", account.paysFor.provider)
                    },

                    """
                        update wallets  
                        set balance = balance + ?amount
                        where 
                            account_id = ?accountId and 
                            account_type = ?accountType and
                            product_category = ?productCategory and
                            product_provider = ?productProvider
                    """
                )
                .rowsAffected

            if (rowsAffected < 1) throw RPCException("Account not found", HttpStatusCode.NotFound)
        }
    }

    suspend fun getReservedCredits(
        ctx: DBContext,
        account: Wallet
    ): Long {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("accountId", account.id)
                setParameter("accountType", account.type.name)
                setParameter("productCategory", account.paysFor.id)
                setParameter("productProvider", account.paysFor.provider)
            }

            session
                .sendPreparedStatement(
                    params,

                    """
                        delete from transactions 
                        where
                            account_id = ?accountId and
                            account_type = ?accountType and
                            product_category = ?productCategory and
                            product_provider = ?productProvider and
                            is_reserved = true and
                            expires_at is not null and
                            expires_at < timezone('utc', now())
                    """
                )

            session
                .sendPreparedStatement(
                    params,

                    """
                        select sum(amount)::bigint
                        from transactions
                        where
                            account_id = ?accountId and
                            account_type = ?accountType and
                            product_category = ?productCategory and
                            product_provider = ?productProvider and
                            is_reserved = true
                    """
                )
                .rows
                .firstOrNull()
                ?.let { it.getLong(0) }
                ?: 0L
        }
    }

    suspend fun reserveCredits(
        ctx: DBContext,
        initiatedBy: Actor,
        account: Wallet,
        reservationId: String,
        amount: Long,
        expiresAt: Long,
        productId: String,
        units: Long
    ) {
        if (initiatedBy == Actor.System) {
            throw IllegalStateException("System cannot initiate a reservation")
        }

        ctx.withSession { session ->
            val (balance, _) = getBalance(ctx, initiatedBy, account, true)
            val reserved = getReservedCredits(ctx, account)
            if (reserved + amount > balance) {
                throw RPCException("Insufficient funds", HttpStatusCode.PaymentRequired)
            }

            session.insert(TransactionTable) {
                set(TransactionTable.accountId, account.id)
                set(TransactionTable.accountType, account.type.name)
                set(TransactionTable.productCategory, account.paysFor.id)
                set(TransactionTable.productProvider, account.paysFor.provider)
                set(TransactionTable.amount, amount)
                set(TransactionTable.expiresAt, LocalDateTime(expiresAt, DateTimeZone.UTC))
                set(TransactionTable.initiatedBy, initiatedBy.username)
                set(TransactionTable.isReserved, true)
                set(TransactionTable.productId, productId)
                set(TransactionTable.units, units)
                set(TransactionTable.completedAt, LocalDateTime.now(DateTimeZone.UTC))
                set(TransactionTable.id, reservationId)
            }
        }
    }

    suspend fun chargeFromReservation(
        ctx: DBContext,
        reservationId: String,
        amount: Long,
        units: Long
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
                            id = ?reservationId and
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
            val productCategory = transaction.getField(TransactionTable.productCategory)
            val productProvider = transaction.getField(TransactionTable.productProvider)

            session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("units", units)
                        setParameter("reservationId", reservationId)
                    },

                    """
                        update transactions     
                        set
                            amount = ?amount,
                            units = ?units,
                            is_reserved = false,
                            completed_at = now(),
                            expires_at = null
                        where
                            id = ?reservationId 
                    """
                )

            session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("accountId", accountId)
                        setParameter("accountType", accountType)
                        setParameter("productCategory", productCategory)
                        setParameter("productProvider", productProvider)
                    },
                    """
                        update wallets
                        set balance = balance - ?amount
                        where
                            account_id = ?accountId and
                            account_type = ?accountType and
                            product_category = ?productCategory and
                            product_provider = ?productProvider
                    """
                )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
