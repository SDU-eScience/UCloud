package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.Utils.CREDITS_NOTIFY_LIMIT
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

object WalletTable : SQLTable("wallets") {
    val accountId = text("account_id", notNull = true)
    val accountType = text("account_type", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val balance = long("balance", notNull = true)
    val lowFundsNotificationSend = bool("low_funds_notifications_send", notNull = true)
}

object TransactionTable : SQLTable("transactions") {
    val accountId = text("account_id", notNull = true)
    val accountType = text("account_type", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)

    /**
     * The original account id
     *
     * This is used in case of projects. The original account id will be the leaf project which was actually charged.
     * A transaction entry for all ancestors is also created, they can look up the original charge by looking at
     * the [originalAccountId].
     *
     * It is implied that the type of this account matches [accountType].
     */
    val originalAccountId = text("original_account_id", notNull = true)

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
    private val verificationService: VerificationService,
    private val client: AuthenticatedClient
) {
    suspend fun requirePermissionToReadBalance(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        walletOwnerType: WalletOwnerType
    ) {
        if (initiatedBy == Actor.System) return
        if (initiatedBy is Actor.User && initiatedBy.principal.role in Roles.PRIVILEGED) return
        if (initiatedBy != Actor.System && initiatedBy.username.startsWith("_")) return

        if (walletOwnerType == WalletOwnerType.USER && initiatedBy.username == accountId) return
        if (walletOwnerType == WalletOwnerType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy.username)

            val membershipOfThis = memberStatus?.membership?.find { it.projectId == accountId }
            if (membershipOfThis != null) {
                return
            }

            if (isAdminOfParentProject(accountId, memberStatus)) return
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
        if (initiatedBy is Actor.User && initiatedBy.principal.role in Roles.PRIVILEGED) return
        if (initiatedBy != Actor.System && initiatedBy.username.startsWith("_")) return

        if (walletOwnerType == WalletOwnerType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy.username)
            if (isAdminOfParentProject(accountId, memberStatus)) return
            projectCache.memberStatus.remove(initiatedBy.username)
            val memberStatus2 = projectCache.memberStatus.get(initiatedBy.username)
            if (isAdminOfParentProject(accountId, memberStatus2)) return
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    private suspend fun isAdminOfParentProject(
        accountId: String,
        memberStatus: UserStatusResponse?
    ): Boolean {
        val ancestors = projectCache.ancestors.get(accountId)
            ?: throw RPCException("Could not retrieve ancestors", HttpStatusCode.BadGateway)

        val thisProject = ancestors.last()
        check(thisProject.id == accountId)

        if (thisProject.parent != null) {
            val parentProject = ancestors[ancestors.lastIndex - 1]
            check(thisProject.parent == parentProject.id)

            val membershipOfParent = memberStatus?.membership?.find { it.projectId == parentProject.id }
            if (membershipOfParent != null && membershipOfParent.whoami.role.isAdmin()) {
                return true
            }
        }
        return false
    }

    suspend fun requirePermissionToTransferFromAccount(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        walletOwnerType: WalletOwnerType
    ) {
        if (initiatedBy == Actor.System) return
        if (initiatedBy is Actor.User && initiatedBy.principal.role in Roles.PRIVILEGED) return
        if (initiatedBy != Actor.System && initiatedBy.username.startsWith("_")) return

        if (walletOwnerType == WalletOwnerType.PROJECT) {
            val memberStatus = projectCache.memberStatus.get(initiatedBy.username)
                ?: throw RPCException("Could not lookup member status", HttpStatusCode.BadGateway)
            val isAdmin = memberStatus.membership.any { it.projectId == accountId && it.whoami.role.isAdmin() }
            if (isAdmin) return
        }

        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun getWalletsForAccount(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        accountOwnerType: WalletOwnerType,
        includeChildren: Boolean
    ): List<WalletBalance> {
        return ctx.withSession { session ->
            requirePermissionToReadBalance(session, initiatedBy, accountId, accountOwnerType)
            verificationService.verify(accountId, accountOwnerType)

            val accountIds = if (accountOwnerType == WalletOwnerType.PROJECT && includeChildren) {
                listOf(accountId) + (projectCache.subprojects.get(accountId)?.map { it.id }
                    ?: throw RPCException("Could not find children", HttpStatusCode.BadGateway))
            } else {
                listOf(accountId)
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("accountIds", accountIds)
                        setParameter("accountType", accountOwnerType.name)
                    },

                    """
                        select w.*, pc.area
                        from wallets w, product_categories pc
                        where 
                            w.account_id in (select unnest(:accountIds::text[])) and 
                            w.account_type = :accountType and
                            pc.category = w.product_category and
                            pc.provider = w.product_provider
                    """
                )
                .rows
                .map {
                    WalletBalance(
                        Wallet(
                            it.getField(WalletTable.accountId),
                            accountOwnerType,
                            ProductCategoryId(
                                it.getField(WalletTable.productCategory),
                                it.getField(WalletTable.productProvider)
                            )
                        ),
                        it.getField(WalletTable.balance),
                        ProductArea.valueOf(it.getString("area")!!)
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
                            account_id = :accountId and 
                            account_type = :accountType and
                            product_category = :productCategory and
                            product_provider = :productProvider
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
                throw RPCException("Balance has been updated since you last viewed it! ($currentBalance / $lastKnownBalance)", HttpStatusCode.Conflict)
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
                    set(WalletTable.lowFundsNotificationSend, false)
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
                        set balance = :amount
                        where 
                            account_id = :accountId and 
                            account_type = :accountType and 
                            product_category = :productCategory and
                            product_provider = :productProvider
                    """
                )
        }
        resetLowFundsNotificationIfNeeded(ctx, account)
    }

    suspend fun resetLowFundsNotificationIfNeeded(
        ctx: DBContext,
        account: Wallet
    ) {
        ctx.withSession { session ->
            val wallet = session
                .sendPreparedStatement(
                    {
                        setParameter("id", account.id)
                        setParameter("type", account.type.name)
                        setParameter("category", account.paysFor.id)
                        setParameter("provider", account.paysFor.provider)
                    },
                    """
                        SELECT * 
                        FROM wallets
                        WHERE account_id = :id 
                            AND account_type = :type 
                            AND product_provider = :provider 
                            AND product_category = :category
                    """
                )
                .rows
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(
                    HttpStatusCode.NotFound,
                    "Not able to get balance"
                )
            val balance = wallet.getField(WalletTable.balance)
            val notified = wallet.getField(WalletTable.lowFundsNotificationSend)

            if (balance >= CREDITS_NOTIFY_LIMIT && notified) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("id", account.id)
                            setParameter("type", account.type.name)
                            setParameter("category", account.paysFor.id)
                            setParameter("provider", account.paysFor.provider)
                            setParameter("status", false)
                        },
                        """
                            UPDATE wallets   
                            SET low_funds_notifications_send = :status
                            WHERE account_id = :id 
                            AND account_type = :type 
                            AND product_provider = :provider 
                            AND product_category = :category
                        """
                    )
            } else {
                //DO Nothing since balance is high and notification does not need to be reset.
            }
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
                        set balance = balance + :amount
                        where 
                            account_id = :accountId and 
                            account_type = :accountType and
                            product_category = :productCategory and
                            product_provider = :productProvider
                    """
                )
                .rowsAffected

            if (rowsAffected < 1) {
                setBalance(session, initiatedBy, account, 0L, amount)
            }
            resetLowFundsNotificationIfNeeded(ctx, account)
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
                            account_id = :accountId and
                            account_type = :accountType and
                            product_category = :productCategory and
                            product_provider = :productProvider and
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
                            account_id = :accountId and
                            account_type = :accountType and
                            product_category = :productCategory and
                            product_provider = :productProvider and
                            is_reserved = true
                    """
                )
                .rows
                .firstOrNull()
                ?.getLong(0)
                ?: 0L
        }
    }

    private class ReservationUserRequestedAbortException : RuntimeException()
    suspend fun reserveCredits(
        ctx: DBContext,
        initiatedBy: Actor,
        request: ReserveCreditsRequest,
        reserveForAncestors: Boolean = true,
        origWallet: Wallet? = null,
        initiatedByUsername: String? = null
    ): Unit = with(request) {
        val wallet = request.account
        val originalWallet = origWallet ?: wallet
        require(originalWallet.paysFor == wallet.paysFor)
        require(originalWallet.type == wallet.type)

        try {
            ctx.withSession { session ->
                val ancestorWallets = if (reserveForAncestors) wallet.ancestors() else emptyList()

                val (balance, _) = getBalance(ctx, initiatedBy, wallet, true)
                val reserved = getReservedCredits(ctx, wallet)
                if (reserved + amount > balance) {
                    throw RPCException("Insufficient funds", HttpStatusCode.PaymentRequired)
                }

                session.insert(TransactionTable) {
                    set(TransactionTable.accountId, wallet.id)
                    set(TransactionTable.accountType, wallet.type.name)
                    set(TransactionTable.productCategory, wallet.paysFor.id)
                    set(TransactionTable.productProvider, wallet.paysFor.provider)
                    set(TransactionTable.amount, amount)
                    set(TransactionTable.expiresAt, LocalDateTime(expiresAt, DateTimeZone.UTC))
                    set(TransactionTable.initiatedBy, initiatedByUsername ?: initiatedBy.safeUsername())
                    set(TransactionTable.isReserved, true)
                    set(TransactionTable.productId, productId)
                    set(TransactionTable.units, productUnits)
                    set(TransactionTable.completedAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                    set(TransactionTable.originalAccountId, originalWallet.id)
                    set(TransactionTable.id, jobId)
                }

                ancestorWallets.forEach { ancestor ->
                    // discardAfterLimitCheck should not be true for children since it would cause an exception to be
                    // thrown too early
                    reserveCredits(
                        session,
                        Actor.System,
                        request.copy(account = ancestor, discardAfterLimitCheck = false),
                        reserveForAncestors = false,
                        origWallet = wallet,
                        initiatedByUsername = initiatedByUsername ?: initiatedBy.safeUsername()
                    )
                }

                if (discardAfterLimitCheck) {
                    throw ReservationUserRequestedAbortException()
                }

                if (chargeImmediately) {
                    chargeFromReservation(session, request.jobId, request.amount, request.productUnits)
                }
            }
        } catch (ignored: ReservationUserRequestedAbortException) {
            // Ignored
        }
    }

    private suspend fun Wallet.ancestors(): List<Wallet> {
        val wallet = this
        return if (wallet.type == WalletOwnerType.PROJECT) {
            val ancestors = projectCache.ancestors.get(wallet.id) ?: throw RPCException(
                "Could not find ancestor wallets",
                HttpStatusCode.InternalServerError
            )

            ancestors
                .asSequence()
                .filter { it.id != this.id }
                .map { Wallet(it.id, WalletOwnerType.PROJECT, wallet.paysFor) }
                .toList()
        } else {
            emptyList()
        }
    }

    suspend fun chargeFromReservation(
        ctx: DBContext,
        reservationId: String,
        amount: Long,
        units: Long
    ) {
        ctx.withSession { session ->
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
                            amount = :amount,
                            units = :units,
                            is_reserved = false,
                            completed_at = now(),
                            expires_at = null
                        where
                            id = :reservationId 
                    """
                )

            session
                .sendPreparedStatement(
                    {
                        setParameter("amount", amount)
                        setParameter("reservationId", reservationId)
                    },

                    """
                        update wallets
                        set balance = balance - :amount
                        where
                            (account_id, account_type, product_category, product_provider) in (
                                select t.account_id, t.account_type, t.product_category, t.product_provider
                                from transactions t
                                where t.id = :reservationId
                            )
                    """
                )
        }
    }

    suspend fun transferToPersonal(
        ctx: DBContext,
        actor: Actor,
        request: SingleTransferRequest
    ) {
        ctx.withSession { session ->
            requirePermissionToTransferFromAccount(session, actor, request.sourceAccount.id, request.sourceAccount.type)

            val id = UUID.randomUUID().toString()
            reserveCredits(
                session,
                actor,
                ReserveCreditsRequest(
                    jobId = id,
                    amount = request.amount,
                    expiresAt = Time.now() + (1000L * 60),
                    account = request.sourceAccount,
                    jobInitiatedBy = actor.safeUsername(),
                    productId = "",
                    productUnits = 1L,
                    chargeImmediately = true
                )
            )

            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("destId", request.destinationAccount.id)
                        setParameter("amount", request.amount)
                        setParameter("prodCategory", request.destinationAccount.paysFor.id)
                        setParameter("prodProvider", request.destinationAccount.paysFor.provider)
                        setParameter("sent", false)
                    },

                    """
                        insert into wallets (account_id, account_type, product_category, product_provider, balance, low_funds_notifications_send) 
                        values (:destId, 'USER', :prodCategory, :prodProvider, :amount, :sent)
                        on conflict (account_id, account_type, product_category, product_provider)
                        do update set balance = wallets.balance + excluded.balance
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
