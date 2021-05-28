package dk.sdu.cloud.accounting.services.wallets

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.products.ProductCategoryTable
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

object WalletTable : SQLTable("accounting.wallets") {
    val accountId = text("account_id", notNull = true)
    val accountType = text("account_type", notNull = true)
    val category = long("category", notNull = true)
    val balance = long("balance", notNull = true)
    val lowFundsNotificationSend = bool("low_funds_notifications_send", notNull = true)

    // The following fields are managed by triggers
    val allocated = long("allocated", notNull = true)
    val used = long("used", notNull = true)
}

const val CREDITS_NOTIFY_LIMIT = 5000000

class BalanceService(
    private val projectCache: ProjectCache,
    private val verificationService: VerificationService,
    private val client: AuthenticatedClient
) {
    suspend fun requirePermissionToReadBalance(
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

    suspend fun retrieveWalletsForProjects(
        ctx: DBContext,
        projectIds: List<String>
    ): List<Wallet> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectIds", projectIds)
                    },
                    """
                        SELECT w.* , pc.category, pc.provider
                        FROM accounting.wallets w join accounting.product_categories pc on w.category = pc.id
                        WHERE account_id IN (SELECT unnest(:projectIds::text[]))
                    """
                ).rows
                .map {
                    Wallet(
                        it.getField(WalletTable.accountId),
                        WalletOwnerType.valueOf(it.getField(WalletTable.accountType)),
                        ProductCategoryId(
                            it.getField(ProductCategoryTable.category),
                            it.getField(ProductCategoryTable.provider)
                        )
                    )
                }
        }
    }

    suspend fun getWalletsForAccount(
        ctx: DBContext,
        initiatedBy: Actor,
        accountId: String,
        accountOwnerType: WalletOwnerType,
        includeChildren: Boolean,
        showHidden: Boolean = true
    ): List<WalletBalance> {
        return ctx.withSession { session ->
            requirePermissionToReadBalance(initiatedBy, accountId, accountOwnerType)
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
                        setParameter("showHidden", showHidden)
                    },
                    """
                        select w.*, pc.area, pc.category, pc.provider
                        from accounting.wallets w join accounting.product_categories pc on w.category = pc.id
                        where 
                            w.account_id in (select unnest(:accountIds::text[])) and 
                            w.account_type = :accountType and
                            exists(
                                select 1
                                from accounting.products p
                                where
                                    pc.id = p.category and
                                    (p.hidden_in_grant_applications is false or :showHidden is true)
                            )
                    """
                )
                .rows
                .map {
                    WalletBalance(
                        Wallet(
                            it.getField(WalletTable.accountId),
                            accountOwnerType,
                            ProductCategoryId(
                                it.getField(ProductCategoryTable.category),
                                it.getField(ProductCategoryTable.provider)
                            )
                        ),
                        it.getField(WalletTable.balance),
                        it.getField(WalletTable.allocated),
                        it.getField(WalletTable.used),
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
            requirePermissionToReadBalance(initiatedBy, account.id, account.type)

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
                        from accounting.wallets w join accounting.product_categories pc on w.category = pc.id
                        where 
                            w.account_id = :accountId and 
                            w.account_type = :accountType and
                            pc.category = :productCategory and
                            pc.provider = :productProvider
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
                throw RPCException(
                    "Balance has been updated since you last viewed it! ($currentBalance / $lastKnownBalance)",
                    HttpStatusCode.Conflict
                )
            }

            if (!exists) {
                if (lastKnownBalance != 0L) {
                    throw RPCException(
                        "Balance has been updated since you last viewed it!",
                        HttpStatusCode.Conflict
                    )
                }
            }

            session.sendPreparedStatement(
                {
                    setParameter("account_id", account.id)
                    setParameter("account_type", account.type.name)
                    setParameter("product_category", account.paysFor.id)
                    setParameter("product_provider", account.paysFor.provider)
                    setParameter("balance", amount)
                },
                """
                    insert into accounting.wallets 
                    (account_id, account_type, category, balance, low_funds_notifications_send) 
                    values (
                        :account_id,
                        :account_type,
                        (
                            select id 
                            from accounting.product_categories pc
                            where
                                pc.category = :product_category and
                                pc.provider = :product_provider
                        ),
                        :balance,
                        false
                    )
                    on conflict (account_id, account_type, category) 
                    do update set 
                        balance = excluded.balance, 
                        low_funds_notifications_send = false 
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
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", account.id)
                        setParameter("type", account.type.name)
                        setParameter("category", account.paysFor.id)
                        setParameter("provider", account.paysFor.provider)
                        setParameter("limit", CREDITS_NOTIFY_LIMIT)
                    },
                    """
                        update accounting.wallets w
                        set low_funds_notifications_send = false
                        where
                            account_id = :id and 
                            account_type = :type and
                            category = (
                                select pc.id 
                                from accounting.product_categories pc
                                where
                                    pc.category = :category and
                                    pc.provider = :provider and
                                    w.category = pc.id
                            ) and
                            balance > :limit
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
                        update accounting.wallets w
                        set balance = balance + :amount
                        where 
                            account_id = :accountId and 
                            account_type = :accountType and
                            category = (
                                select pc.id
                                from accounting.product_categories pc
                                where
                                    w.category = pc.id and
                                    pc.category = :productCategory and
                                    pc.provider = :productProvider
                            )
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
                        delete from accounting.transactions t
                        where
                            wallet = (
                                select w.id
                                from 
                                    accounting.wallets w join 
                                    accounting.product_categories pc on w.category = pc.id
                                where 
                                    t.wallet = w.id
                                    account_id = :accountId and
                                    account_type = :accountType and
                                    pc.category = :productCategory and
                                    pc.provider = :productProvider
                            ) and
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
                        from 
                            accounting.transactions t join accounting.wallets w on t.wallet = w.id join
                            accounting.product_categories pc on w.category = pc.id
                        where
                            w.account_id = :accountId and
                            w.account_type = :accountType and
                            pc.category = :productCategory and
                            pc.provider = :productProvider and
                            t.is_reserved = true
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
        initiatedBy: String,
        request: ReserveCreditsRequest,
        reserveForAncestors: Boolean = true,
        origWallet: Wallet? = null,
    ): Unit = with(request) {
        log.info("reserveCredits($initiatedBy, $request, $reserveForAncestors, $origWallet)")
        val wallet = request.account
        val originalWallet = origWallet ?: wallet
        require(originalWallet.paysFor == wallet.paysFor)
        require(originalWallet.type == wallet.type)

        try {
            ctx.withSession { session ->
                val ancestorWallets = if (reserveForAncestors) wallet.ancestors() else emptyList()

                val (balance, walletExists) = getBalance(ctx, Actor.System, wallet, true)
                if (!walletExists) {
                    setBalance(session, Actor.System, request.account, 0L, 0L)
                }

                if (!request.skipLimitCheck) {
                    val reserved = getReservedCredits(ctx, wallet)
                    if (reserved + amount > balance) {
                        throw RPCException(
                            "Insufficient funds",
                            HttpStatusCode.PaymentRequired,
                            run {
                                val product = Products.findProduct.call(
                                    FindProductRequest(
                                        wallet.paysFor.provider,
                                        wallet.paysFor.id,
                                        request.productId
                                    ), client
                                ).orNull()
                                if (product == null) null
                                else "NOT_ENOUGH_${product.area.name}_CREDITS"
                            }
                        )
                    }
                }

                if (request.skipIfExists) {
                    val exists = session
                        .sendPreparedStatement(
                            {
                                setParameter("id", jobId)
                                setParameter("accId", wallet.id)
                                setParameter("accType", wallet.type.name)
                            },
                            """
                                select exists(
                                    select 1
                                    from accounting.transactions t join accounting.wallets w on t.wallet = w.id
                                    where 
                                        t.id = :id and 
                                        w.account_id = :accId and 
                                        w.account_type = :accType
                                )
                            """
                        )
                        .rows.single().getBoolean(0)!!

                    if (exists) {
                        return@withSession // bail out
                    }
                }

                if (!discardAfterLimitCheck) {
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("account_id", wallet.id)
                                setParameter("account_type", wallet.type.name)
                                setParameter("product_category", wallet.paysFor.id)
                                setParameter("product_provider", wallet.paysFor.provider)
                                setParameter("amount", amount)
                                setParameter("expires_at", expiresAt)
                                setParameter("initiated_by", initiatedBy)
                                setParameter("product_id", productId)
                                setParameter("units", productUnits)
                                setParameter("original_account_id", originalWallet.id)
                                setParameter("job_id", jobId)
                                setParameter("transaction_comment", transactionComment(amount, wallet.id,
                                    transactionType))
                            },
                            """
                                insert into accounting.transactions
                                (id, units, amount, initiated_by, completed_at, original_account_id, expires_at, 
                                 transaction_comment, wallet, product) 
                                values(
                                    :job_id,
                                    :units,
                                    :amount,
                                    :initiated_by,
                                    now(),
                                    :original_account_id,
                                    to_timestamp(:expires_at / 1000),
                                    :transaction_comment,
                                    (
                                        select w.id
                                        from 
                                            accounting.wallets w join accounting.product_categories pc
                                                on w.category = pc.id
                                        where
                                            pc.category = :product_category and
                                            pc.provider = :product_provider and
                                            w.account_id = :account_id and
                                            w.account_type = :account_type
                                    ),
                                    (
                                        select p.id
                                        from
                                            accounting.products p join accounting.product_categories pc 
                                                on p.category = pc.id
                                        where
                                            pc.category = :product_category and
                                            pc.provider = :product_provider and
                                            p.name = :product_id
                                    )
                                )
                            """
                        )
                }

                ancestorWallets.forEach { ancestor ->
                    // discardAfterLimitCheck should not be true for children since it would cause an exception to be
                    // thrown too early
                    //
                    // skipIfExists should only be true for the initial reservation since if it passes then none of
                    // the parents should have it
                    reserveCredits(
                        session,
                        initiatedBy,
                        request.copy(
                            account = ancestor,
                            discardAfterLimitCheck = false,
                            skipIfExists = false,
                            chargeImmediately = false,
                        ),
                        reserveForAncestors = false,
                        origWallet = wallet,
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
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }

            throw ex
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
                        update accounting.transactions     
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
                        update accounting.wallets w
                        set balance = greatest(0, balance - :amount)
                        where
                            w.id = (
                                select t.wallet
                                from accounting.transactions t
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
                actor.safeUsername(),
                ReserveCreditsRequest(
                    jobId = id,
                    amount = request.amount,
                    expiresAt = Time.now() + (1000L * 60),
                    account = request.sourceAccount,
                    jobInitiatedBy = actor.safeUsername(),
                    productId = "",
                    productUnits = 1L,
                    chargeImmediately = true,
                    transactionType = TransactionType.TRANSFERRED_TO_PERSONAL
                )
            )

            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("destId", request.destinationAccount.id)
                        setParameter("amount", request.amount)
                        setParameter("prodCategory", request.destinationAccount.paysFor.id)
                        setParameter("prodProvider", request.destinationAccount.paysFor.provider)
                    },
                    """
                        insert into accounting.wallets 
                        (account_id, account_type, category, balance, low_funds_notifications_send) 
                        values (
                            :destId, 
                            'USER', 
                            (
                                select pc.id
                                from accounting.product_categories pc
                                where
                                    pc.category = :prodCategory and
                                    pc.provider = :prodProvider
                            ),
                            :amount, 
                            false
                        )
                        on conflict (account_id, account_type, category)
                        do update set balance = wallets.balance + excluded.balance
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun grantProviderCredits(ctx: DBContext, actorAndProject: ActorAndProject, provider: String) {
        val (actor, project) = actorAndProject
        if (actor !is Actor.User || actor.principal.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        ctx.withSession { session ->
            val categories = session
                .sendPreparedStatement(
                    { setParameter("provider", provider) },
                    """
                        select category from accounting.product_categories where provider = :provider
                    """
                )
                .rows
                .map { it.getString(0)!! }

            for (category in categories) {
                addToBalance(
                    session,
                    Actor.System,
                    Wallet(
                        if (project != null) project else actor.username,
                        if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                        ProductCategoryId(category, provider)
                    ),
                    1_000_000_000L
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
