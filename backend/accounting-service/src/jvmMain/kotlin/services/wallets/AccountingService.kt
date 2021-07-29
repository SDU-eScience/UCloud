package dk.sdu.cloud.accounting.services.wallets

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.ChargeWalletRequestItem
import dk.sdu.cloud.accounting.api.DepositToWalletRequestItem
import dk.sdu.cloud.accounting.api.RootDepositRequestItem
import dk.sdu.cloud.accounting.api.TransferToWalletRequestItem
import dk.sdu.cloud.accounting.api.UpdateAllocationRequestItem
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.PostgresErrorCodes
import dk.sdu.cloud.service.db.async.TransactionMode
import dk.sdu.cloud.service.db.async.errorCode
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.parameterList
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString

class AccountingService(
    val db: DBContext,
) {
    private val transactionMode = TransactionMode.Serializable(readWrite = true)

    suspend fun charge(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ) {
        val actor = actorAndProject.actor
        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role != Role.SERVICE)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        db.withSession(remapExceptions = true, transactionMode) { session ->
            session.sendPreparedStatement(
                packChargeRequests(request),
                """
                    with requests as (
                        select (
                            unnest(:payer_ids::text[]),
                            unnest(:payer_is_project::boolean[]),
                            unnest(:units::bigint[]),
                            unnest(:number_of_products::bigint[]),
                            unnest(:product_ids::text[]),
                            unnest(:product_categories::text[]),
                            unnest(:product_provider::text[]),
                            unnest(:performed_by::text[]),
                            unnest(:descriptions::text[]) 
                        )::accounting.charge_request req
                    )
                    select accounting.charge(array_agg(req))
                    from requests;
                """
            )
        }
    }

    suspend fun check(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ): BulkResponse<Boolean> {
        val actor = actorAndProject.actor
        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role != Role.SERVICE)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return BulkResponse(
            db.withSession(remapExceptions = true, transactionMode) { session ->
                session.sendPreparedStatement(
                    packChargeRequests(request),
                    """
                        with requests as (
                            select (
                                unnest(:payer_ids::text[]),
                                unnest(:payer_is_project::boolean[]),
                                unnest(:units::bigint[]),
                                unnest(:number_of_products::bigint[]),
                                unnest(:product_ids::text[]),
                                unnest(:product_categories::text[]),
                                unnest(:product_provider::text[]),
                                unnest(:performed_by::text[]),
                                unnest(:descriptions::text[]) 
                            )::accounting.charge_request req
                        )
                        select accounting.credit_check(array_agg(req))
                        from requests;
                    """
                ).rows.map { it.getBoolean(0)!! }
            }
        )
    }

    private fun packChargeRequests(request: BulkRequest<ChargeWalletRequestItem>): EnhancedPreparedStatement.() -> Unit =
        {
            val payerIds by parameterList<String>()
            val payerIsProject by parameterList<Boolean>()
            val units by parameterList<Long>()
            val numberOfProducts by parameterList<Long>()
            val productIds by parameterList<String>()
            val productCategories by parameterList<String>()
            val productProvider by parameterList<String>()
            val performedBy by parameterList<String>()
            val descriptions by parameterList<String>()
            for (req in request.items) {
                when (val payer = req.payer) {
                    is WalletOwner.Project -> {
                        payerIds.add(payer.projectId)
                        payerIsProject.add(true)
                    }
                    is WalletOwner.User -> {
                        payerIds.add(payer.username)
                        payerIsProject.add(false)
                    }
                }
                units.add(req.units)
                numberOfProducts.add(req.numberOfProducts)
                productIds.add(req.product.id)
                productCategories.add(req.product.category)
                productProvider.add(req.product.provider)
                performedBy.add(req.performedBy)
                descriptions.add(req.description)
            }
        }

    suspend fun deposit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DepositToWalletRequestItem>,
    ) {
        db.withSession(remapExceptions = true, transactionMode) { session ->
            session.sendPreparedStatement(
                {
                    val initiatedBy by parameterList<String>()
                    val recipients by parameterList<String>()
                    val recipientIsProject by parameterList<Boolean>()
                    val sourceAllocation by parameterList<Long?>()
                    val desiredBalance by parameterList<Long>()
                    val startDates by parameterList<Long?>()
                    val endDates by parameterList<Long?>()
                    val descriptions by parameterList<String>()
                    for (req in request.items) {
                        initiatedBy.add(actorAndProject.actor.safeUsername())
                        when (val recipient = req.recipient) {
                            is WalletOwner.Project -> {
                                recipients.add(recipient.projectId)
                                recipientIsProject.add(true)
                            }
                            is WalletOwner.User -> {
                                recipients.add(recipient.username)
                                recipientIsProject.add(false)
                            }
                        }
                        sourceAllocation.add(req.sourceAllocation.toLongOrNull())
                        desiredBalance.add(req.amount)
                        startDates.add(req.startDate?.let { it / 1000 })
                        endDates.add(req.endDate?.let { it / 1000 })
                        descriptions.add(req.description)
                    }
                },
                """
                    with requests as (
                        select (
                            unnest(:initiated_by::text[]),
                            unnest(:recipients::text[]),
                            unnest(:recipient_is_project::boolean[]),
                            unnest(:source_allocation::bigint[]),
                            unnest(:desired_balance::bigint[]),
                            to_timestamp(unnest(:start_dates::bigint[])),
                            to_timestamp(unnest(:end_dates::bigint[])),
                            unnest(:descriptions::text[])
                        )::accounting.deposit_request req
                    )
                    select accounting.deposit(array_agg(req))
                    from requests
                """
            )
        }
    }

    suspend fun rootDeposit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<RootDepositRequestItem>
    ) {
        db.withSession(remapExceptions = true, transactionMode) { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                val productCategories by parameterList<String>()
                val productProviders by parameterList<String>()
                val usernames by parameterList<String?>()
                val projectIds by parameterList<String?>()
                val startDates by parameterList<Long?>()
                val endDates by parameterList<Long?>()
                val balances by parameterList<Long?>()
                val descriptions by parameterList<String?>()
                setParameter("actor", actorAndProject.actor.safeUsername())
                for (req in request.items) {
                    productCategories.add(req.categoryId.name)
                    productProviders.add(req.categoryId.provider)
                    usernames.add((req.recipient as? WalletOwner.User)?.username)
                    projectIds.add((req.recipient as? WalletOwner.Project)?.projectId)
                    startDates.add(req.startDate?.let { it / 1000 })
                    endDates.add(req.endDate?.let { it / 1000 })
                    balances.add(req.amount)
                    descriptions.add(req.description)
                }
            }

            try {
                session.sendPreparedStatement(
                    {
                        parameters()
                        retain("usernames", "project_ids")
                    },
                    """
                    insert into accounting.wallet_owner (username, project_id) 
                    values (unnest(:usernames::text[]), unnest(:project_ids::text[]))
                    on conflict do nothing
                """
                )
            } catch (ex: GenericDatabaseException) {
                if (ex.errorCode == PostgresErrorCodes.FOREIGN_KEY_VIOLATION) {
                    throw RPCException("No such payer exists", HttpStatusCode.BadRequest)
                }
            }

            session.sendPreparedStatement(
                {
                    parameters()
                    retain("product_categories", "product_providers", "usernames", "project_ids")
                },
                """
                    with requests as (
                        select
                            unnest(:product_categories::text[]) product_category,
                            unnest(:product_providers::text[]) product_provider,
                            unnest(:usernames::text[]) username,
                            unnest(:project_ids::text[]) project_id
                    )
                    insert into accounting.wallets (category, owned_by) 
                    select pc.id, wo.id
                    from
                        requests req join
                        accounting.product_categories pc on
                            req.product_category = pc.category and
                            req.product_provider = pc.provider join
                        accounting.wallet_owner wo on
                            req.username = wo.username or
                            req.project_id = wo.project_id
                    on conflict do nothing
                """
            )

            val rowsAffected = session.sendPreparedStatement(
                parameters,
                """
                    with 
                        requests as (
                            select 
                                nextval('accounting.wallet_allocations_id_seq') alloc_id,
                                unnest(:product_categories::text[]) product_category,
                                unnest(:product_providers::text[]) product_provider,
                                unnest(:usernames::text[]) username,
                                unnest(:project_ids::text[]) project_id,
                                to_timestamp(unnest(:start_dates::bigint[])) start_date,
                                to_timestamp(unnest(:end_dates::bigint[])) end_date,
                                unnest(:balances::bigint[]) balance,
                                unnest(:descriptions::text[]) description,
                                :actor actor
                        ),
                        new_allocations as (
                            insert into accounting.wallet_allocations
                                (id, associated_wallet, balance, initial_balance, start_date, end_date, allocation_path) 
                            select
                                req.alloc_id,
                                w.id, req.balance, req.balance, coalesce(req.start_date, now()), req.end_date,
                                req.alloc_id::text::ltree
                            from
                                requests req join
                                accounting.product_categories pc on
                                    req.product_category = pc.category and
                                    req.product_provider = pc.provider join
                                accounting.wallets w on w.category = pc.id join
                                accounting.wallet_owner wo on
                                    w.owned_by = wo.id and
                                    req.username = wo.username or
                                    req.project_id = wo.project_id
                            returning id, balance
                        )
                    insert into accounting.transactions
                        (type, affected_allocation_id, action_performed_by, change, description, start_date)
                    select 'deposit', alloc.id, r.actor, alloc.balance, r.description, coalesce(r.start_date, now())
                    from
                        new_allocations alloc join
                        requests r on alloc.id = r.alloc_id
                """
            ).rowsAffected

            if (rowsAffected != request.items.size.toLong()) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
        }
    }

    suspend fun transfer(
        actorAndProject: ActorAndProject,
        request: BulkRequest<TransferToWalletRequestItem>,
    ) {
        TODO()
    }

    suspend fun updateAllocation(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateAllocationRequestItem>,
    ) {
        db.withSession(remapExceptions = true, transactionMode) { session ->
            session.sendPreparedStatement(
                {
                    val ids by parameterList<Long?>()
                    val balance by parameterList<Long>()
                    val startDates by parameterList<Long>()
                    val endDates by parameterList<Long?>()
                    val descriptions by parameterList<String>()
                    setParameter("performed_by", actorAndProject.actor.safeUsername())
                    for (req in request.items) {
                        ids.add(req.id.toLongOrNull())
                        balance.add(req.balance)
                        descriptions.add(req.reason)
                        startDates.add(req.startDate / 1000)
                        endDates.add(req.endDate?.let { it / 1000 })
                    }
                },
                """
                    with requests as (
                        select (
                            :performed_by,
                            unnest(:ids::bigint[]),
                            to_timestamp(unnest(:start_dates::bigint[])),
                            to_timestamp(unnest(:end_dates::bigint[])),
                            unnest(:descriptions::text[]),
                            unnest(:balance::bigint[])
                        )::accounting.allocation_update_request req
                    )
                    select accounting.update_allocations(array_agg(req))
                    from requests
                """
            )
        }
    }

    suspend fun browseWallets(
        actorAndProject: ActorAndProject,
        request: WalletBrowseRequest
    ): PageV2<Wallet> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        declare c cursor for
                        select accounting.wallet_to_json(w, wo, array_agg(alloc), pc)
                        from
                            accounting.wallets w join
                            accounting.wallet_owner wo on w.owned_by = wo.id join
                            accounting.wallet_allocations alloc on w.id = alloc.associated_wallet join
                            accounting.product_categories pc on w.category = pc.id left join
                            project.project_members pm on wo.project_id = pm.project_id
                        where
                            wo.username = :user or
                            (pm.username = :user and pm.project_id = :project::text)
                        group by w.*, wo.*, pc.*, pc.provider, pc.category
                        order by
                            pc.provider, pc.category
                    """
                )
            },
            mapper = { _, rows ->
                rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
