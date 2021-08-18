package dk.sdu.cloud.accounting.services.wallets

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
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
import okhttp3.internal.toLongOrDefault

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
                ).rowsAffected.also { println("owners $it") }
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
            ).rowsAffected

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
                                (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
                                allocation_path) 
                            select
                                req.alloc_id,
                                w.id, req.balance, req.balance, req.balance, coalesce(req.start_date, now()),
                                req.end_date, req.alloc_id::text::ltree
                            from
                                requests req join
                                accounting.product_categories pc on
                                    req.product_category = pc.category and
                                    req.product_provider = pc.provider join
                                accounting.wallet_owner wo on
                                    req.username = wo.username or
                                    req.project_id = wo.project_id join
                                accounting.wallets w on
                                    w.category = pc.id and
                                    w.owned_by = wo.id
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
        db.withSession(remapExceptions = true, transactionMode) { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                val sourceIds by parameterList<String>()
                val sourcesAreProjects by parameterList<Boolean>()
                val targetIds by parameterList<String>()
                val targetsAreProjects by parameterList<Boolean>()
                val amounts by parameterList<Long>()
                val categories by parameterList<String>()
                val providers by parameterList<String>()
                val startDates by parameterList<Long?>()
                val endDates by parameterList<Long?>()
                val performedBy by parameterList<String>()
                val descriptions by parameterList<String>()
                for (req in request.items) {
                    val sourceId = when (val source = req.source) {
                        is WalletOwner.Project -> {
                            sourcesAreProjects.add(true)
                            sourceIds.add(source.projectId)
                            source.projectId
                        }
                        is WalletOwner.User -> {
                            sourcesAreProjects.add(false)
                            sourceIds.add(source.username)
                            source.username
                        }
                    }
                    val targetId = when (val target = req.target) {
                        is WalletOwner.Project -> {
                            targetsAreProjects.add(true)
                            targetIds.add(target.projectId)
                            target.projectId
                        }
                        is WalletOwner.User -> {
                            targetsAreProjects.add(false)
                            targetIds.add(target.username)
                            target.username
                        }
                    }
                    amounts.add(req.amount)
                    categories.add(req.categoryId.name)
                    providers.add(req.categoryId.provider)
                    startDates.add(req.startDate?.let { it / 1000 })
                    endDates.add(req.endDate?.let { it / 1000 })
                    performedBy.add(req.performedBy)
                    descriptions.add("Transfer from $sourceId to $targetId")
                }
            }

            session.sendPreparedStatement(
                {
                    parameters()
                },
                """
                    with requests as (
                        select (
                            unnest(:source_ids::text[]),
                            unnest(:sources_are_projects::bool[]),
                            unnest(:target_ids::text[]),
                            unnest(:targets_are_projects::bool[]),
                            unnest(:amounts::bigint[]),
                            unnest(:categories::text[]),
                            unnest(:providers::text[]),
                            to_timestamp(unnest(:start_dates::bigint[])),
                            to_timestamp(unnest(:end_dates::bigint[])),
                            unnest(:performed_by::text[]),
                            unnest(:descriptions::text[])
                        )::accounting.transfer_request req
                    )
                    select accounting.credit_check(array_agg(req))
                    from requests;
                """, debug = true
            ).rows
                .forEach {
                    if (!it.getBoolean(0)!!) {
                        throw RPCException.fromStatusCode(HttpStatusCode.PaymentRequired)
                    }
                }
            //Charge the source wallet and create transfer transaction
            session.sendPreparedStatement(
                {
                    parameters()
                },
                """
                    with requests as (
                        select (
                            unnest(:source_ids::text[]),
                            unnest(:sources_are_projects::bool[]),
                            unnest(:target_ids::text[]),
                            unnest(:targets_are_projects::bool[]),
                            unnest(:amounts::bigint[]),
                            unnest(:categories::text[]),
                            unnest(:providers::text[]),
                            to_timestamp(unnest(:start_dates::bigint[])),
                            to_timestamp(unnest(:end_dates::bigint[])),
                            unnest(:performed_by::text[]),
                            unnest(:descriptions::text[])
                        )::accounting.transfer_request req
                    )
                    select accounting.transfer(array_agg(req))
                    from requests
                """.trimIndent()
            )

            //make deposit to target wallet
            session.sendPreparedStatement(
                {
                    parameters()
                    retain("categories", "providers", "targets_are_projects", "target_ids")
                },
                """
                    with requests as (
                        select
                            unnest(:categories::text[]) product_category,
                            unnest(:providers::text[]) product_provider,
                            unnest(:targets_are_projects::bool[]) is_project,
                            unnest(:target_ids::text[]) account_id
                    )
                    insert into accounting.wallets (category, owned_by) 
                    select pc.id, wo.id
                    from
                        requests req join
                        accounting.product_categories pc on
                            req.product_category = pc.category and
                            req.product_provider = pc.provider join
                        accounting.wallet_owner wo on 
                            ( req.is_project and req.account_id = wo.project_id) 
                            or (not req.is_project and req.account_id = wo.username)
                           
                    on conflict do nothing
                """
            )
            println("create allocation")
            val rowsAffected = session.sendPreparedStatement(
                {
                    parameters()
                },
                """
                    with 
                        requests as (
                            select 
                                nextval('accounting.wallet_allocations_id_seq') alloc_id,
                                unnest(:categories::text[]) product_category,
                                unnest(:providers::text[]) product_provider,
                                unnest(:targets_are_projects::bool[]) is_project,
                                unnest(:target_ids::text[]) account_id,
                                unnest(:amounts::bigint[]) balance,
                                to_timestamp(unnest(:start_dates::bigint[])) start_date,
                                to_timestamp(unnest(:end_dates::bigint[])) end_date,    
                                unnest(:performed_by::text[]) performed_by, 
                                unnest(:descriptions::text[]) description
                        ),
                        new_allocations as (
                            insert into accounting.wallet_allocations
                                (id, associated_wallet, balance, initial_balance, local_balance, start_date, end_date,
                                allocation_path) 
                            select
                                req.alloc_id,
                                w.id, req.balance, req.balance, req.balance, coalesce(req.start_date, now()),
                                req.end_date, req.alloc_id::text::ltree
                            from
                                requests req join
                                accounting.product_categories pc on
                                    req.product_category = pc.category and
                                    req.product_provider = pc.provider join
                                accounting.wallet_owner wo on
                                    (req.is_project and req.account_id = wo.project_id) or
                                    (not req.is_project and req.account_id = wo.username) join
                                accounting.wallets w on
                                    w.category = pc.id and
                                    w.owned_by = wo.id
                            returning id, balance
                        )
                    insert into accounting.transactions
                        (type, affected_allocation_id, action_performed_by, change, description, start_date)
                    select 'deposit', alloc.id, r.performed_by, alloc.balance, r.description, coalesce(r.start_date, now())
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
                            (:project::text is null and wo.username = :user) or
                            (:project::text is not null and pm.username = :user and pm.project_id = :project::text)
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

    suspend fun browseTransactions(
        actorAndProject: ActorAndProject,
        request: TransactionsBrowseRequest
    ): PageV2<Transaction> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("filter_category", request.filterCategory)
                        setParameter("filter_provider", request.filterProvider)
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        declare c cursor for
                        select accounting.transaction_to_json(t, p, pc)
                        from
                            accounting.transactions t join
                            accounting.wallet_allocations alloc on t.affected_allocation_id = alloc.id join
                            accounting.wallets w on alloc.associated_wallet = w.id join
                            accounting.product_categories pc on w.category = pc.id join
                            accounting.wallet_owner wo on w.owned_by = wo.id left join
                            project.project_members pm on wo.project_id = pm.project_id left join
                            accounting.products p on pc.id = p.category and t.product_id = p.id
                        where
                            (
                                :project::text is null or
                                wo.project_id = :project
                            ) and
                            (
                                pm.username = :user or
                                wo.username = :user
                            ) and
                            (
                                :filter_category::text is null or
                                :filter_category = pc.category
                            ) and
                            (
                                :filter_provider::text is null or
                                :filter_provider = pc.provider
                            )
                        order by 
                            w.id, alloc.id, t.created_at desc
                    """
                )
            },
            mapper = { _, rows -> rows.map { defaultMapper.decodeFromString(it.getString(0)!!) } }
        )
    }

    suspend fun browseSubAllocations(
        actorAndProject: ActorAndProject,
        request: WalletsBrowseSubAllocationsRequest
    ): PageV2<SubAllocation> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                        setParameter("filter_type", request.filterType?.name)
                    },
                    """
                        declare c cursor for
                        select
                            jsonb_build_object(
                                'workspaceTitle', coalesce(alloc_project.title, alloc_owner.username),
                                'workspaceIsProject', alloc_project.id is not null,
                                'remaining', alloc.balance,
                                'productCategoryId', jsonb_build_object(
                                    'name', pc.category,
                                    'provider', pc.provider
                                ),
                                'chargeType', pc.charge_type,
                                'unit', 'ABSOLUTE' -- TODO
                            )
                        from
                            accounting.wallet_owner owner join
                            accounting.wallets owner_wallets on owner.id = owner_wallets.owned_by join
                            accounting.product_categories pc on owner_wallets.category = pc.id join
                            
                            accounting.wallet_allocations owner_allocations on
                                owner_wallets.id = owner_allocations.associated_wallet join
                            
                            accounting.wallet_allocations alloc on
                                owner_allocations.allocation_path @> alloc.allocation_path join
                            accounting.wallets alloc_wallet on alloc.associated_wallet = alloc_wallet.id join
                            
                            accounting.wallet_owner alloc_owner on alloc_wallet.owned_by = alloc_owner.id left join
                            project.projects alloc_project on alloc_owner.project_id = alloc_project.id left join
                            
                            project.project_members owner_pm on
                                owner.project_id = owner_pm.project_id and
                                owner.username = :username and
                                (owner_pm.role = 'ADMIN' or owner_pm.role = 'PI')
                        where
                            (
                                (
                                    :project::text is not null and
                                    owner.project_id = :project and
                                    owner_pm.username is not null
                                ) or
                                (
                                    :project::text is null and
                                    owner.username = :username
                                )
                            ) and
                            (
                                :filter_type::accounting.product_type is null or
                                pc.product_type = :filter_type::accounting.product_type
                            )
                        order by pc.provider, pc.category, alloc.id
                    """
                )
            },
            mapper = { _, rows -> rows.map { defaultMapper.decodeFromString(it.getString(0)!!) } }
        )
    }

    suspend fun retrieveUsage(
        actorAndProject: ActorAndProject,
        request: VisualizationRetrieveUsageRequest
    ): VisualizationRetrieveUsageResponse {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val now = Time.now()
                    setParameter("start_date", request.filterStartDate ?: (now - (1000L * 60 * 60 * 24 * 7)))
                    setParameter("end_date", request.filterEndDate ?: now)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    setParameter("filter_provider", request.filterProvider)
                    setParameter("filter_category", request.filterProductCategory)
                    setParameter("filter_type", request.filterType?.name)
                    setParameter("filter_allocation", request.filterAllocation?.toLongOrDefault(-1))
                    setParameter("num_buckets", 30 as Int)
                },
                """
 with
    -- NOTE(Dan): We start by fetching all relevant transactions. We combine the transactions with information
    -- from the associated product category. The category is crucial to create charts which make sense.
    -- This section is also the only section which fetches data from an actual table. If this code needs optimization
    -- then this is most likely the place to look.
    all_transactions as (
        select
            t.created_at, t.change, t.units, t.source_allocation_id, pc.category, pc.provider, pc.charge_type,
            pc.product_type, pc.unit_of_price
        from
            accounting.wallet_owner wo join
            accounting.wallets w on w.owned_by = wo.id join
            accounting.wallet_allocations alloc on alloc.associated_wallet = w.id  join
            accounting.transactions t on t.affected_allocation_id = alloc.id join
            accounting.product_categories pc on w.category = pc.id left join
            project.project_members pm on
                pm.project_id = wo.project_id and
                (pm.role = 'ADMIN' or pm.role = 'PI')
        where
            t.type = 'charge' and
            t.created_at >= to_timestamp(:start_date / 1000.0) and
            t.created_at <= to_timestamp(:end_date / 1000.0) and
            (
                (wo.project_id = :project::text and pm.username = :username) or
                (:project::text is null and wo.username = :username)
            ) and
            (
                :filter_type::accounting.product_type is null or
                pc.product_type = :filter_type::accounting.product_type
            ) and
            (
                :filter_provider::text is null or
                pc.provider = :filter_provider
            ) and
            (
                :filter_category::text is null or
                pc.category = :filter_category
            ) and
            (
                :filter_allocation::bigint is null or
                alloc.id = :filter_allocation
            )
    ),
    -- NOTE(Dan): Next we split up our data processing into two separate tracks, for a little while. The first
    -- track will process `DIFFERENTIAL_QUOTA`. This path will use the units to track actual usage recorded. Unlike
    -- the `ABSOLUTE` track, we will be picking the last recorded entry if multiple entries fall into the same bucket.
    -- To start with we will produce multiple results per bucket, these will be differentiated by the
    -- source_allocation_id. This allows us to capture usage from different sub-allocations.
    units_per_bucket as (
        select
            category, provider, charge_type, product_type, unit_of_price,
            width_bucket(
                provider.timestamp_to_unix(transaction.created_at),
                :start_date,
                :end_date,
                :num_buckets - 1
            ) as bucket,
            provider.last(units) as data_point
        from all_transactions transaction
        where charge_type = 'DIFFERENTIAL_QUOTA'
        group by category, provider, charge_type, product_type, unit_of_price, source_allocation_id, bucket
    ),
    -- NOTE(Dan): We now combine the data from multiple sub-allocations into a single entry per bucket. We do this by
    -- simply summing the total usage in each bucket.
    units_per_bucket_sum as (
        select category, provider, charge_type, product_type, unit_of_price, bucket, sum(data_point) as data_point
        from units_per_bucket
        group by category, provider, charge_type, product_type, unit_of_price, bucket
        order by provider, category, bucket
    ),
    -- NOTE(Dan): We now switch our processing back to the `ABSOLUTE` type products. These products are a bit simpler
    -- given that we simply need to sum up all changes, we don't need to pick any specific recording from a bucket since
    -- all records in a bucket are relevant for us. This section will give us a data point which represents the total
    -- change inside of a single bucket.
    change_per_bucket as (
        select
            category, provider, charge_type, product_type, unit_of_price,
            width_bucket(
                provider.timestamp_to_unix(transaction.created_at),
                :start_date,
                :end_date,
                :num_buckets - 1
            ) as bucket,
            sum(change) as data_point
        from all_transactions transaction
        where charge_type = 'ABSOLUTE'
        group by category, provider, charge_type, product_type, unit_of_price, bucket
        order by provider, category, bucket
    ),
    -- NOTE(Dan): We now transform the change (which is negative) into usage (the inverse). At the same time we
    -- compute a rolling sum to get a chart which always trend up.
    change_per_bucket_sum as (
        select
            category, provider, charge_type, product_type, unit_of_price, bucket,
            sum(data_point) over (partition by category, provider order by bucket) * -1 as data_point
        from change_per_bucket
        order by provider, category, product_type, unit_of_price, bucket
    ),
    -- NOTE(Dan): We know merge the two separate branches into a unified branch. We now have all data needed to produce
    -- the charts.
    all_entries as (
        select * from change_per_bucket_sum
        union
        select * from units_per_bucket_sum
    ),
    -- NOTE(Dan): The clients don't care about buckets, they care about concrete timestamps. In this section we convert
    -- the bucket index into an actual timestamp. While doing so, we fetch the total usage in the period, we can do this
    -- by simply picking the last data point.
    bucket_to_timestamp as (
        select
            ceil((bucket - 1) * ((:end_date - :start_date) / :num_buckets::double precision) + :start_date) as ts,
            data_point,
            l.period_usage,
            e.category, e.provider, e.charge_type, e.product_type, e.unit_of_price
        from
            all_entries e join
            (
                select category, provider, charge_type, provider.last(data_point) period_usage
                from all_entries
                group by category, provider, charge_type
            ) l on
                e.category = l.category and
                e.provider = l.provider and
                e.charge_type = l.charge_type
    ),
    -- NOTE(Dan): We now start our marshalling to JSON by first combining all data points into lines.
    point_aggregation as (
        select
            category, provider, charge_type, product_type, unit_of_price, period_usage,
            array_agg(jsonb_build_object(
                'timestamp', ts,
                'value', data_point
            )) points
        from bucket_to_timestamp
        group by category, provider, charge_type, product_type, unit_of_price, period_usage
    ),
    -- NOTE(Dan): The lines are then combined into complete charts.
    chart_aggregation as (
        select jsonb_build_object(
            'type', product_type,
            'chargeType', charge_type,
            'unit', unit_of_price,
            'periodUsage', sum(period_usage),
            'chart', jsonb_build_object(
                'lines', array_agg(jsonb_build_object(
                    'name', category || ' / ' || provider,
                    'points', points
                ))
            )
        ) chart
        from point_aggregation
        group by product_type, charge_type, unit_of_price
    ),
    -- NOTE(Dan): And the charts are combined into a single output for consumption by UCloud/Core.
    combined_charts as (
        select jsonb_build_object('charts', array_remove(array_agg(chart), null))
        from chart_aggregation
    )
select * from combined_charts;
                """
            )
        }.rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) } ?:
            throw RPCException(
                "No usage data found. Are you sure you are allowed to view the data?",
                HttpStatusCode.NotFound
            )
    }

    suspend fun retrieveBreakdown(
        actorAndProject: ActorAndProject,
        request: VisualizationRetrieveBreakdownRequest
    ): VisualizationRetrieveBreakdownResponse {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val now = Time.now()
                    setParameter("start_date", request.filterStartDate ?: (now - (1000L * 60 * 60 * 24 * 7)))
                    setParameter("end_date", request.filterEndDate ?: now)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    setParameter("filter_provider", request.filterProvider)
                    setParameter("filter_category", request.filterProductCategory)
                    setParameter("filter_type", request.filterType?.name)
                    setParameter("filter_allocation", request.filterAllocation?.toLongOrDefault(-1))
                },
                """
 with
    -- NOTE(Dan): We start by fetching all relevant transactions. We combine the transactions with information
    -- from the associated product category. The category is crucial to create charts which make sense.
    -- This section is also the only section which fetches data from an actual table. If this code needs optimization
    -- then this is most likely the place to look.
    all_transactions as (
        select
            t.created_at, t.change, t.units, t.source_allocation_id, pc.category, pc.provider, pc.charge_type,
            pc.product_type, pc.unit_of_price, p.name as product
        from
            accounting.wallet_owner wo join
            accounting.wallets w on w.owned_by = wo.id join
            accounting.wallet_allocations alloc on alloc.associated_wallet = w.id  join
            accounting.transactions t on t.affected_allocation_id = alloc.id join
            accounting.products p on t.product_id = p.id join
            accounting.product_categories pc on p.category = pc.id left join
            project.project_members pm on
                pm.project_id = wo.project_id and
                (pm.role = 'ADMIN' or pm.role = 'PI')
        where
            t.type = 'charge' and
            t.created_at >= to_timestamp(:start_date / 1000.0) and
            t.created_at <= to_timestamp(:end_date / 1000.0) and
            (
                (wo.project_id = :project::text and pm.username = :username) or
                (:project::text is null and wo.username = :username)
            ) and
            (
                :filter_type::accounting.product_type is null or
                pc.product_type = :filter_type::accounting.product_type
            ) and
            (
                :filter_provider::text is null or
                pc.provider = :filter_provider
            ) and
            (
                :filter_category::text is null or
                pc.category = :filter_category
            )
    ),
    -- NOTE(Dan): We pick the latest recording for every source_allocation_id
    units_per_bucket as (
        select
            category, provider, charge_type, product_type, unit_of_price, product,
            provider.last(units) as data_point
        from all_transactions transaction
        where charge_type = 'DIFFERENTIAL_QUOTA'
        group by category, provider, charge_type, product_type, unit_of_price, product, source_allocation_id
    ),
    -- NOTE(Dan): Similar to the usage, we need to sum these together
    units_per_bucket_sum as (
        select category, provider, charge_type, product_type, unit_of_price, product, sum(data_point) as data_point
        from units_per_bucket
        group by category, provider, charge_type, product_type, unit_of_price, product
    ),
    -- NOTE(Dan): As opposed to usage, we can take a more direct route to compute the concrete period usage
    change_per_bucket_sum as (
        select
            category, provider, charge_type, product_type, unit_of_price, product,
            sum(change) * -1 as data_point
        from all_transactions transaction
        where charge_type = 'ABSOLUTE'
        group by category, provider, charge_type, product_type, unit_of_price, product
    ),
    -- NOTE(Dan): We know merge the two separate branches into a unified branch. We now have all data needed to produce
    -- the charts.
    all_entries as (
        select * from change_per_bucket_sum
        union
        select * from units_per_bucket_sum
    ),
    -- NOTE(Dan): We rank every row of every chart to determine the top-3 of every chart
    ranked_categories as (
        select
            e.category, e.provider, e.charge_type, e.product_type, e.unit_of_price, e.product, e.data_point,
            row_number() over (partition by e.category, e.provider order by data_point desc) rank
        from all_entries e
    ),
    -- NOTE(Dan): We use this information to combine entries with rank > 3 into a single entry
    collapse_others as (
        select charge_type, product_type, unit_of_price, data_point,
            product || ' / ' || category || ' / ' || provider as name
        from ranked_categories
        where rank <= 3
        union
        select charge_type, product_type, unit_of_price, sum(data_point), 'Other' as name
        from ranked_categories
        where rank > 3
        group by charge_type, product_type, unit_of_price
    ),
    -- NOTE(Dan): Once we have this building the chart is straight-forward
    chart_aggregation as (
        select jsonb_build_object(
            'type', product_type,
            'chargeType', charge_type,
            'unit', unit_of_price,
            'chart', jsonb_build_object(
                'points', array_agg(
                    jsonb_build_object(
                        'name', name,
                        'value', data_point
                    )
                )
            )
        ) chart
        from collapse_others
        group by product_type, charge_type, unit_of_price
    ),
    combined_charts as (
        select jsonb_build_object('charts', array_remove(array_agg(chart), null))
        from chart_aggregation
    )
select * from combined_charts;
                   
                """
            ).rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) } ?: throw RPCException(
                "No usage data found. Are you sure you are allowed to view the data?",
                HttpStatusCode.NotFound
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
