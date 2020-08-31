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

                val (balance, walletExists) = getBalance(ctx, initiatedBy, wallet, true)
                if (!walletExists) {
                    setBalance(session, Actor.System, request.account, 0L, 0L)
                }

                if (!request.skipLimitCheck) {
                    val reserved = getReservedCredits(ctx, wallet)
                    if (reserved + amount > balance) {
                        throw RPCException("Insufficient funds", HttpStatusCode.PaymentRequired)
                    }
                }

                if (request.skipIfExists) {
                    val exists = (session
                        .sendPreparedStatement(
                            {
                                setParameter("id", jobId)
                                setParameter("accId", wallet.id)
                                setParameter("accType", wallet.type.name)
                            },

                            """
                                select count(id)::bigint from transactions 
                                where id = :id and account_id = :accId and account_type = :accType
                            """
                        )
                        .rows.single().getLong(0) ?: 0L) > 0L

                    if (exists) {
                        return@withSession // bail out
                    }
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
                    //
                    // skipIfExists should only be true for the initial reservation since if it passes then none of
                    // the parents should have it
                    reserveCredits(
                        session,
                        Actor.System,
                        request.copy(account = ancestor, discardAfterLimitCheck = false, skipIfExists = false),
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
                        set balance = greatest(0, balance - :amount)
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

fun main() {
    val allUsers = """
        0aaa0b15-5525-447c-a7d0-5141aab90c16
        11537ebc-d919-43d8-9b37-e7dc69c4e6ec
        1#2138
        17#1361
        1f999d2f-4b98-4ea4-89ee-ec2721487e01
        40243bbf-44f8-4ecc-9a5e-7ef4536f8002
        4dec34df-12e6-4456-86de-ae9a34ff7a42
        58145e02-5bb1-4d9f-99a8-e80495d2b153
        596c4c14-97c2-4171-b322-3bcfe226eba5
        5b7bbe14-5a08-42c5-9bae-ea9250f84077
        5e18642f-042f-4cbd-beb9-d6335c10d29a
        62a7bea7-dcd4-4b1c-8535-940b1867366e
        65fdb6e5-9e43-4257-828a-3bc76da10667
        75be867d-e72f-4710-b8fc-b8667fcf68ac
        853e5aac-c6ae-4869-a112-646453b6680c
        8a23a24c-7130-4489-a4b2-65ebd426e9f5
        8a7e90aa-2920-4abf-a601-cd9b99e07444
        8f6f4f5d-6a65-4066-a0a2-2ae3eef2173d
        910c443e-d364-4954-bfa1-f97c0426a670
        abb56186-c31e-4fc6-8cc8-5b7aae477b18
        b2cae915-c495-41df-8e9c-39052599aa2e
        bb72b9af-6a5c-4f82-9e15-7b4b15838271
        bcbd11bc-6f5b-4005-bec6-28c17d283735
        c1abb7dd-fd9c-43ba-ba19-b185f622b802
        c4780725-9f06-46ea-8fa3-e6c9b5fb7d2e
        ce00c21e-d2a7-4934-bfbf-6cf16d721645
        'Class 2020'
        d5ea0121-cb66-4d04-a287-8ae2d43d0746
        DevOps
        ec13e046-1275-429b-ade1-ed6d8a01a1eb
        f564edfb-7dac-47c9-a17d-355829b03468
        f61da441-1258-4c5a-8ab6-0f28b9b1db1e
        fa49ed4a-bd00-4d29-a7ab-f48bb84222fe
        Fie
        geez#4942
        {GET:host:dev.cloud.sdu.dk}
        {GET:query:itemsPerPage:25}
        {GET:query:order:ASCENDING}
        {GET:query:page:0}
        {GET:query:sortBy:path}
        {GET:remote:{Address:10.135.0.200:443}}
        {GET:scheme:https}
        'Hansens Awesome World-renowend Test Problem'
        Hello#8832
        HenrikProject#7559
        'My project'
        Test
        Test-12-05-14:25
        Test#9224
    """.trimIndent()

    val realUsers = """
        596c4c14-97c2-4171-b322-3bcfe226eba5
        ec13e046-1275-429b-ade1-ed6d8a01a1eb
        65fdb6e5-9e43-4257-828a-3bc76da10667
        c1abb7dd-fd9c-43ba-ba19-b185f622b802
        abb56186-c31e-4fc6-8cc8-5b7aae477b18
        5e18642f-042f-4cbd-beb9-d6335c10d29a
        8a23a24c-7130-4489-a4b2-65ebd426e9f5
        ce00c21e-d2a7-4934-bfbf-6cf16d721645
        f61da441-1258-4c5a-8ab6-0f28b9b1db1e
        75be867d-e72f-4710-b8fc-b8667fcf68ac
        1f999d2f-4b98-4ea4-89ee-ec2721487e01
        853e5aac-c6ae-4869-a112-646453b6680c
        5b7bbe14-5a08-42c5-9bae-ea9250f84077
        4dec34df-12e6-4456-86de-ae9a34ff7a42
        b2cae915-c495-41df-8e9c-39052599aa2e
        58145e02-5bb1-4d9f-99a8-e80495d2b153
        8a7e90aa-2920-4abf-a601-cd9b99e07444
        11537ebc-d919-43d8-9b37-e7dc69c4e6ec
        d5ea0121-cb66-4d04-a287-8ae2d43d0746
        c4780725-9f06-46ea-8fa3-e6c9b5fb7d2e
        8f6f4f5d-6a65-4066-a0a2-2ae3eef2173d
        62a7bea7-dcd4-4b1c-8535-940b1867366e
        fa49ed4a-bd00-4d29-a7ab-f48bb84222fe
        bcbd11bc-6f5b-4005-bec6-28c17d283735
        910c443e-d364-4954-bfa1-f97c0426a670
        40243bbf-44f8-4ecc-9a5e-7ef4536f8002
        bb72b9af-6a5c-4f82-9e15-7b4b15838271
        0aaa0b15-5525-447c-a7d0-5141aab90c16
    """.trimIndent()

    val realUserList = realUsers.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val allUserList = allUsers.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    for (user in allUserList) {
        if (user !in realUserList) {
            println("mv \"/mnt/cephfs/projects/$user\" \"/mnt/cephfs/old_dev_projects/$user\"")
        }
    }
}
