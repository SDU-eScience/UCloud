package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString

class AccountingService(
    val db: DBContext,
    val providers: Providers<SimpleProviderCommunication>,
    private val processor: AccountingProcessor,
) {
    private fun WalletOwner.toProcessorOwner(): String {
        return when (this) {
            is WalletOwner.Project -> projectId
            is WalletOwner.User -> username
        }
    }

    suspend fun retriveActiveProcessorAddress(): String? {
        return processor.retrieveActiveProcessor()
    }

    suspend fun retrieveAllocationsInternal(
        actorAndProject: ActorAndProject,
        owner: WalletOwner,
        categoryId: ProductCategoryId
    ): List<WalletAllocation> {
        return processor.retrieveAllocationsInternal(AccountingRequest.RetrieveAllocationsInternal(
            actorAndProject.actor,
            owner.toProcessorOwner(),
            categoryId
        )).allocations
    }

    suspend fun resetCache() {
        processor.clearCache()
    }

    suspend fun retrieveWalletsInternal(actorAndProject: ActorAndProject, walletOwner: WalletOwner): List<Wallet> {
        return processor.retrieveWalletsInternal((AccountingRequest.RetrieveWalletsInternal(
            actorAndProject.actor,
            walletOwner.toProcessorOwner()
        ))).wallets
    }

    suspend fun charge(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ): BulkResponse<Boolean> {
        val result = request.items.map { charge ->
            processor.charge(
                AccountingRequest.Charge.ProductUse(
                    actorAndProject.actor,
                    charge.payer.toProcessorOwner(),
                    dryRun = false,
                    charge.units,
                    charge.periods,
                    charge.product
                )
            ).success
        }

        return BulkResponse(result)
    }

    suspend fun check(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ): BulkResponse<Boolean> {
        val result = request.items.map { charge ->
            processor.charge(
                AccountingRequest.Charge.ProductUse(
                    actorAndProject.actor,
                    charge.payer.toProcessorOwner(),
                    dryRun = true,
                    charge.units,
                    charge.periods,
                    charge.product
                )
            ).success
        }
        return BulkResponse(result)
    }

    suspend fun checkIfSubAllocationIsAllowed(allocs: List<String>, ctx: DBContext = db) {
        ctx.withSession { session ->
            val allowedToTransferFromCount = session.sendPreparedStatement(
                {
                    setParameter("source_allocs", allocs.mapNotNull { it.toLongOrNull() })
                },
                """
                    select count(*)
                    from
                        accounting.wallet_allocations alloc
                    where
                        alloc.id = some(:source_allocs::bigint[]) and
                        alloc.can_allocate
                """
            ).rows.firstOrNull()?.getLong(0) ?: 0L

            if (allowedToTransferFromCount.toInt() != allocs.size) {
                throw RPCException(
                    "One or more of your allocations do not allow sub-allocations. Try a different source allocation.",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }

    class DryRunException : RuntimeException("Dry run - Aborting")

    suspend fun deposit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DepositToWalletRequestItem>,
    ) {
        var isDry: Boolean? = null
        for (item in request.items) {
            if (isDry != null && item.dry != isDry) {
                throw RPCException(
                    "The entire transaction must be either dry or wet. You cannot mix items in a single transaction.",
                    HttpStatusCode.BadRequest
                )
            }

            isDry = item.dry
        }

        request.items.map { deposit ->
            checkIfSubAllocationIsAllowed(listOf(deposit.sourceAllocation), db)
            processor.deposit(
                AccountingRequest.Deposit(
                    actorAndProject.actor,
                    deposit.recipient.toProcessorOwner(),
                    deposit.sourceAllocation.toIntOrNull() ?: return@map,
                    deposit.amount,
                    deposit.startDate ?: Time.now(),
                    deposit.endDate
                )
            )
        }
            val providerIds = db.withSession { session ->

            session.sendPreparedStatement(
                {
                    request.items.split {
                        into("source_allocation") { it.sourceAllocation.toLongOrNull() ?: -1 }
                    }
                },
                """
                    select distinct pc.provider
                    from
                        accounting.wallet_allocations alloc join
                        accounting.wallets w on alloc.associated_wallet = w.id join
                        accounting.product_categories pc on w.category = pc.id
                    where
                        alloc.id = some(:source_allocation::bigint[])
                """
                ).rows.map { it.getString(0)!! }
            }

            providerIds.forEach { provider ->
                val comms = providers.prepareCommunication(provider)
                DepositNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
            }
    }

    suspend fun rootDeposit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<RootDepositRequestItem>
    ) {
        request.items.map { deposit ->
            processor.rootDeposit(AccountingRequest.RootDeposit(
                Actor.System,
                deposit.recipient.toProcessorOwner(),
                deposit.categoryId,
                deposit.amount,
                forcedSync = deposit.forcedSync
            ))
        }
    }

    suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<RegisterWalletRequestItem>
    ) {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)

        db.withSession { session ->
            val duplicateTransactions = session.sendPreparedStatement(
                {
                    setParameter("transaction_ids", request.items.map { providerId + it.uniqueAllocationId })
                },
                """
                    select transaction_id
                    from accounting.transactions
                    where transaction_id = some(:transaction_ids::text[])
                """
            ).rows.map { it.getString(0)!! }.toSet()

            val requestsToFulfill =
                request.items.filter { (providerId + it.uniqueAllocationId) !in duplicateTransactions }

            rootDeposit(
                ActorAndProject(Actor.System, null),
                BulkRequest(requestsToFulfill.map { reqItem ->
                    RootDepositRequestItem(
                        ProductCategoryId(reqItem.categoryId, providerId),
                        reqItem.owner,
                        reqItem.balance,
                        "Allocation registered outside of UCloud",
                        transactionId = providerId + reqItem.uniqueAllocationId,
                        providerGeneratedId = reqItem.providerGeneratedId
                    )
                })
            )
        }
    }

    suspend fun updateAllocation(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateAllocationRequestItem>,
    ) {
        request.items.map { update ->
            processor.update(AccountingRequest.Update(
                actorAndProject.actor,
                update.id.toIntOrNull() ?: return@map,
                update.balance,
                update.startDate,
                update.endDate,
            ))
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
                        setParameter("filter_type", request.filterType?.name)
                        setParameter("filter_empty", request.filterEmptyAllocations)
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
                            (
                                (:project::text is null and wo.username = :user) or
                                (:project::text is not null and pm.username = :user and pm.project_id = :project::text)
                            ) and
                            (
                                :filter_type::accounting.product_type is null or
                                pc.product_type = :filter_type::accounting.product_type
                            ) and (
                                :filter_empty::bool is null or 
                                (
                                    :filter_empty = true and
                                    alloc.balance > 0
                                ) or (
                                    :filter_empty = false
                                )
                            )
                        group by w.*, wo.*, pc.*, pc.provider, pc.category
                        order by
                            pc.provider, pc.category
                    """,
                    "Accounting Browse Wallets"
                )
            },
            mapper = { _, rows ->
                rows.map {
                    var wallet = defaultMapper.decodeFromString<Wallet>(it.getString(0)!!)
                    if (request.includeMaxUsableBalance == true) {
                        wallet = processor.includeMaxUsableBalance(wallet, request.filterEmptyAllocations)
                    }
                    wallet
                }
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
                    """,
                    "Accounting Browse Transactions"
                )
            },
            mapper = { _, rows -> rows.map { defaultMapper.decodeFromString(it.getString(0)!!) } }
        )
    }

    suspend fun browseSubAllocations(
        actorAndProject: ActorAndProject,
        request: SubAllocationQuery,
        query: String? = null,
    ): PageV2<SubAllocation> {
        val owner = if (actorAndProject.project == null) actorAndProject.actor.safeUsername() else actorAndProject.project!!

        val hits = processor.browseSubAllocations(AccountingRequest.BrowseSubAllocations(actorAndProject.actor, owner, request.filterType, query))
        if (hits.allocations.isEmpty()) { return PageV2(request.itemsPerPage ?: 50, emptyList(), null) }

        val numberOfItems = request.itemsPerPage ?: 50

        return if (request.next == null) {
            PageV2(
                numberOfItems,
                hits.allocations.chunked(numberOfItems)[0],
                if (numberOfItems > hits.allocations.size) null else 1.toString()
            )
        } else {
            val next = request.next!!.toInt()
            val results = hits.allocations.chunked(numberOfItems)[next]

            PageV2(
                numberOfItems,
                results,
                if (results.size < numberOfItems) null else (next + 1).toString()
            )
        }
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
                    setParameter("filter_allocation", request.filterAllocation?.toLongOrNull())
                    setParameter("filter_workspace", request.filterWorkspace)
                    setParameter("filter_workspace_project", request.filterWorkspaceProject)
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
                                accounting.wallet_allocations source_allocation on t.source_allocation_id = source_allocation.id join
                                accounting.wallets source_wallet on source_allocation.associated_wallet = source_wallet.id join
                                accounting.wallet_owner source_owner on source_owner.id = source_wallet.owned_by join
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
                                    t.source_allocation_id = :filter_allocation::bigint
                                ) and
                                (
                                    :filter_workspace::text is null or
                                    (
                                        (
                                            :filter_workspace_project::boolean = true and
                                            source_owner.project_id = :filter_workspace
                                        ) or
                                        (
                                            :filter_workspace_project::boolean is distinct from true and
                                            source_owner.username = :filter_workspace
                                        )
                                    )
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
                                    from (select * from all_entries order by bucket) t
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
                            select jsonb_build_object('charts', coalesce(array_remove(array_agg(chart), null), array[]::jsonb[])) result
                            from chart_aggregation
                        )
                    select * from combined_charts;
                """,
                "Accounting Retrieve Usage",
            )
        }.rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) } ?: throw RPCException(
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
                    setParameter("start_date", request.filterStartDate ?: (now - (1000L * 60 * 60 * 24 * 30)))
                    setParameter("end_date", request.filterEndDate ?: now)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    setParameter("filter_provider", request.filterProvider)
                    setParameter("filter_category", request.filterProductCategory)
                    setParameter("filter_type", request.filterType?.name)
                    setParameter("filter_allocation", request.filterAllocation?.toLongOrNull())
                    setParameter("filter_workspace", request.filterWorkspace)
                    setParameter("filter_workspace_project", request.filterWorkspaceProject)
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
                                accounting.wallet_allocations source_allocation on t.source_allocation_id = source_allocation.id join
                                accounting.wallets source_wallet on source_allocation.associated_wallet = source_wallet.id join
                                accounting.wallet_owner source_owner on source_owner.id = source_wallet.owned_by join
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
                                ) and
                                (
                                    :filter_allocation::bigint is null or
                                    t.source_allocation_id = :filter_allocation::bigint
                                ) and
                                (
                                    :filter_workspace::text is null or
                                    (
                                        (
                                            :filter_workspace_project::boolean = true and
                                            source_owner.project_id = :filter_workspace
                                        ) or
                                        (
                                            :filter_workspace_project::boolean is distinct from true and
                                            source_owner.username = :filter_workspace
                                        )
                                    )
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
                            select jsonb_build_object('charts', coalesce(array_remove(array_agg(chart), null), array[]::jsonb[]))
                            from chart_aggregation
                        )
                    select * from combined_charts;
                """,
                "Accounting Retrieve Breakdown"
            ).rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) } ?: throw RPCException(
                "No usage data found. Are you sure you are allowed to view the data?",
                HttpStatusCode.NotFound
            )
        }
    }

    suspend fun retrieveRecipient(
        actorAndProject: ActorAndProject,
        request: WalletsRetrieveRecipientRequest
    ): WalletsRetrieveRecipientResponse {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", request.query)
                },
                """
                    with
                        projects as (
                            select (t.p).*
                            from (
                                select provider.last(p) as p
                                from unnest(project.find_by_path(:id)) p
                            ) t
                        ),
                        project_size as (
                            select p.id, p.title, count(*) number_of_members
                            from
                                projects p join
                                project.project_members pm on p.id = pm.project_id
                            group by p.id, p.title
                        ),
                        entries as (
                            select
                                p.id as id,
                                true as is_project,
                                p.title as title,
                                pi.username as pi,
                                p.number_of_members as number_of_members
                            from
                                project_size p join
                                project.project_members pi on
                                    p.id = pi.project_id and
                                    pi.role = 'PI'
                            union
                            select
                                principal.id as id,
                                false as is_project,
                                principal.id as title,
                                principal.id as pi,
                                1 as number_of_members
                            from auth.principals principal
                            where
                                principal.id = :id and
                                (
                                    principal.dtype = 'WAYF' or
                                    principal.dtype = 'PASSWORD'
                                )
                        )
                    select jsonb_build_object(
                        'id', id,
                        'isProject', is_project,
                        'title', title,
                        'principalInvestigator', pi,
                        'numberOfMembers', number_of_members
                    ) as result
                    from entries
                """,
                "Accounting Retrieve Recipient"
            ).rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) }
                ?: throw RPCException("Unknown user or project", HttpStatusCode.NotFound)
        }
    }

    suspend fun retrieveProviderSummary(
        actorAndProject: ActorAndProject,
        request: WalletsRetrieveProviderSummaryRequest,
        ctx: DBContext = db,
    ): PageV2<ProviderWalletSummary> {
        // This function will retrieve all relevant wallets for a provider and summarize the allocations of each wallet.
        // The keys used for sorting are stable and essentially map to the provider ID, which we can efficiently query.
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        val itemsPerPage = request.normalize().itemsPerPage

        data class WalletSummary(
            val walletId: Long,
            val ownerUsername: String?,
            val ownerProject: String?,
            val category: String,
            val productType: ProductType,
            val chargeType: ChargeType,
            val unitOfPrice: ProductPriceUnit,

            val allocId: Long,
            val allocBalance: Long,
            val allocInitialBalance: Long,
            val allocPath: List<Long>,

            val ancestorId: Long?,
            val ancestorBalance: Long?,
            val ancestorInitialBalance: Long?,

            val notBefore: Long,
            val notAfter: Long?,
        )

        return ctx.withSession { session ->
            // NOTE(Dan): We start out by fetching all the relevant data. The query is structured in two parts.
            //
            // The first part retrieves the relevant wallets, this performs pagination as early as possible and also
            // filters the query such that we only retrieve the relevant wallets. The second part retrieves all
            // relevant allocations along with their ancestors.
            //
            // This summary is then used to build the summary required by the provider.
            val rowSummary: List<WalletSummary> = session.sendPreparedStatement(
                {
                    setParameter("provider_id", providerId)
                    setParameter("next", request.next?.toLongOrNull())
                    setParameter("owner_id", request.filterOwnerId)
                    setParameter("owner_is_project", request.filterOwnerIsProject ?: false)
                    setParameter("filter_category", request.filterCategory)
                },
                """
                    with
                        relevant_wallets as (
                            select
                                w.id as wallet_id,
                                wo.username as wo_username,
                                wo.project_id as wo_project,
                                pc.category as product_category,
                                pc.product_type as product_type,
                                pc.charge_type as charge_type,
                                pc.unit_of_price as unit_of_price
                                
                            from
                                accounting.wallets w join
                                accounting.wallet_owner wo on
                                    w.owned_by = wo.id join
                                accounting.product_categories pc on
                                    w.category = pc.id
                            where 
                                pc.provider = :provider_id and
                                (
                                    :next::bigint is null or
                                    w.id > :next::bigint
                                ) and
                                (
                                    :owner_id::text is null or
                                    wo.project_id = :owner_id::text or
                                    wo.username = :owner_id::text
                                ) and
                                (
                                    :owner_id::text is null or
                                    (:owner_is_project and wo.project_id is not null) or
                                    (not :owner_is_project and wo.username is not null)
                                ) and
                                (
                                    :filter_category::text is null or
                                    pc.category = :filter_category::text
                                )
                            
                            order by w.id
                            limit $itemsPerPage
                        )
                        
                    select 
                        w.*,
                        alloc.id as alloc_id,
                        alloc.balance as alloc_balance,
                        alloc.initial_balance as alloc_initial_balance,
                        alloc.allocation_path::text as alloc_path,
                        provider.timestamp_to_unix(alloc.start_date)::bigint as not_before,
                        provider.timestamp_to_unix(alloc.end_date)::bigint as not_after,
                        ancestor_alloc.id as ancestor_id,
                        ancestor_alloc.balance as ancestor_balance,
                        ancestor_alloc.initial_balance as ancestor_initial_balance
                    
                    from
                        relevant_wallets w join
                        accounting.wallet_allocations alloc on
                            alloc.associated_wallet = w.wallet_id left join
                        accounting.wallet_allocations ancestor_alloc on
                            ancestor_alloc.allocation_path @> alloc.allocation_path and
                            ancestor_alloc.allocation_path != alloc.allocation_path
                            
                    where
                        now() >= alloc.start_date and
                        (alloc.end_date is null or now() <= alloc.end_date) and
                        
                        (ancestor_alloc.start_date is null or now() >= ancestor_alloc.start_date) and
                        (ancestor_alloc.end_date is null or now() <= ancestor_alloc.end_date)
                """
            ).rows.map { row ->
                WalletSummary(
                    row.getLong("wallet_id")!!,
                    row.getString("wo_username"),
                    row.getString("wo_project"),
                    row.getString("product_category")!!,
                    ProductType.valueOf(row.getString("product_type")!!),
                    ChargeType.valueOf(row.getString("charge_type")!!),
                    ProductPriceUnit.valueOf(row.getString("unit_of_price")!!),

                    row.getLong("alloc_id")!!,
                    row.getLong("alloc_balance")!!,
                    row.getLong("alloc_initial_balance")!!,
                    row.getString("alloc_path")!!.split(".").map { it.toLong() },

                    row.getLong("ancestor_id"),
                    row.getLong("ancestor_balance"),
                    row.getLong("ancestor_initial_balance"),

                    row.getLong("not_before") ?: 0L,
                    row.getLong("not_after"),
                )
            }

            // NOTE(Dan): Next, we build a quick map to map an allocation ID to its current balance. This is used in
            // the next step to determine max usable balance by allocation.
            val balanceByAllocation = HashMap<Long, Long>()
            val initialBalanceByAllocation = HashMap<Long, Long>()
            for (row in rowSummary) {
                balanceByAllocation[row.allocId] = row.allocBalance
                initialBalanceByAllocation[row.allocId] = row.allocInitialBalance
                if (row.ancestorId != null && row.ancestorBalance != null && row.ancestorInitialBalance != null) {
                    balanceByAllocation[row.ancestorId] = row.ancestorBalance
                    initialBalanceByAllocation[row.ancestorId] = row.ancestorInitialBalance
                }
            }

            // NOTE(Dan): To build a max usable by allocation, we start by filtering rows, such that we only have one
            // per allocation. Recall that the query selected multiple of these per allocation to fetch all ancestors.
            val summaryPerAllocation = rowSummary.asSequence().distinctBy { it.allocId }

            // NOTE(Dan): Obtaining the maximum usable by allocation is as simple as finding the smallest balance in an
            // allocation path. It doesn't matter which element it is, we can never use more than the smallest number.
            val unorderedSummary = summaryPerAllocation.map { alloc ->
                val maxUsable = alloc.allocPath.minOf { balanceByAllocation.getValue(it) }
                val maxPromised = alloc.allocPath.minOf { initialBalanceByAllocation.getValue(it) }
                ProviderWalletSummary(
                    alloc.walletId.toString(),
                    when {
                        alloc.ownerProject != null -> WalletOwner.Project(alloc.ownerProject)
                        alloc.ownerUsername != null -> WalletOwner.User(alloc.ownerUsername)
                        else -> error("Corrupt database data for wallet: ${alloc.walletId}")
                    },
                    ProductCategoryId(alloc.category, providerId),
                    alloc.productType,
                    alloc.chargeType,
                    alloc.unitOfPrice,
                    maxUsable,
                    maxPromised,
                    alloc.notBefore,
                    alloc.notAfter
                )
            }

            val summaryItems = unorderedSummary.sortedBy { it.id.toLongOrNull() }.toList()
            val next = summaryItems.lastOrNull()?.id

            PageV2(itemsPerPage, summaryItems, next)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
