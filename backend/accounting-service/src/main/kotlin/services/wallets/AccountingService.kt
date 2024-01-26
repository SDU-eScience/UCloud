package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.services.providers.ProviderService
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.messages.BinaryAllocator
import dk.sdu.cloud.messages.BinaryTypeList
import dk.sdu.cloud.provider.api.ProviderIncludeFlags
import dk.sdu.cloud.provider.api.translateToProductPriceUnit
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.StaticTimeProvider
import dk.sdu.cloud.service.SystemTimeProvider
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AccountingService(
    val devMode: Boolean,
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

    suspend fun retrieveActiveProcessorAddress(): String? {
        return processor.retrieveActiveProcessor()
    }

    suspend fun retrieveTimeFittingAllocations(actorAndProject: ActorAndProject, owner: WalletOwner,  category: ProductCategoryIdV2, start: Long, end: Long): List<WalletAllocationV2> {
        return processor.retrieveAllocationsInternal(
            AccountingRequest.RetrieveAllocationsInternal(actorAndProject.actor, owner.toProcessorOwner(), category)
        ).allocations.filter {
            //Every alloc that start before start of period, but ends within
            (it.startDate <= start && it.endDate > start)
            //Every alloc that starts within time limit of period but also ends within time limit of period
                    || (it.startDate >= start && it.endDate <= end)
            //Every alloc that starts within time limit of period but ends after gift period
                    || (it.startDate <= end && it.endDate >= end)
            //Every alloc that spans the entire period without starting and ending
                    || (it.startDate <= start && it.endDate >= end)
        }
    }
    suspend fun retrieveAllocationsInternal(
        actorAndProject: ActorAndProject,
        owner: WalletOwner,
        categoryId: ProductCategoryIdV2
    ): List<WalletAllocationV2> {
        return processor.retrieveAllocationsInternal(
            AccountingRequest.RetrieveAllocationsInternal(
                actorAndProject.actor,
                owner.toProcessorOwner(),
                categoryId
            )
        ).allocations
    }

    suspend fun findRelevantProviders(
        actorAndProject: ActorAndProject,
        username: String,
        project: String?,
        useProject: Boolean,
        filterProductType: ProductType? = null,
    ): List<String> {
        return processor.findRelevantProviders(
            AccountingRequest.FindRelevantProviders(
                actorAndProject.actor,
                username,
                project,
                useProject,
                filterProductType,
            )
        ).providers
    }

    suspend fun resetState() {
        processor.resetState()
    }

    suspend fun retrieveWalletsInternal(actorAndProject: ActorAndProject, walletOwner: WalletOwner): List<WalletV2> {
        return processor.retrieveWalletsInternal(
            (AccountingRequest.RetrieveWalletsInternal(
                actorAndProject.actor,
                walletOwner.toProcessorOwner()
            ))
        ).wallets
    }

    suspend fun charge(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
        dryRun: Boolean = false
    ): BulkResponse<Boolean> {
        val result = request.items.map { charge ->
            processor.charge(
                AccountingRequest.Charge.OldCharge(
                    actorAndProject.actor,
                    charge.payer.toProcessorOwner(),
                    dryRun,
                    charge.units,
                    charge.periods,
                    ProductReferenceV2(charge.product.id, charge.product.category, charge.product.provider)
                )
            ).success
        }
        return BulkResponse(result)
    }

    suspend fun chargeDelta(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DeltaReportItem>,
        dryRun: Boolean
    ): BulkResponse<Boolean> {
        return BulkResponse(
            request.items.map { charge ->
                processor.charge(
                    AccountingRequest.Charge.DeltaCharge(
                        actorAndProject.actor,
                        charge.owner.toProcessorOwner(),
                        dryRun,
                        charge.categoryIdV2,
                        charge.usage,
                        charge.description
                    )
                ).success
            }
        )
    }

    suspend fun chargeTotal(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DeltaReportItem>,
        dryRun: Boolean
    ): BulkResponse<Boolean> {
        return BulkResponse(
            request.items.map { charge ->
                processor.charge(
                    AccountingRequest.Charge.TotalCharge(
                        actorAndProject.actor,
                        charge.owner.toProcessorOwner(),
                        dryRun,
                        charge.categoryIdV2,
                        charge.usage,
                        charge.description
                    )
                ).success
            }
        )
    }

    suspend fun checkIfAllocationIsAllowed(allocs: List<String>) {
        if (!processor.checkIfAllocationIsAllowed(allocs)) {
            throw RPCException(
                "One or more of your allocations do not allow sub-allocations. Try a different source allocation.",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun checkIfSubAllocationIsAllowed(allocs: List<String>) {
        if (!processor.checkIfSubAllocationIsAllowed(allocs)) {
            throw RPCException(
                "One or more of your allocations do not allow sub-allocations. Try a different source allocation.",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun subAllocate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<SubAllocationRequestItem>,
    ): List<FindByStringId> {
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

        val response = request.items.map { deposit ->
            checkIfAllocationIsAllowed(listOf(deposit.parentAllocation))
            val created = processor.deposit(
                AccountingRequest.Deposit(
                    actorAndProject.actor,
                    deposit.owner.toProcessorOwner(),
                    deposit.parentAllocation.toIntOrNull() ?: throw RPCException.fromStatusCode(
                        HttpStatusCode.BadRequest,
                        "Root deposits should be made with rootAllocate call."
                    ),
                    deposit.quota,
                    deposit.start,
                    deposit.end,
                    grantedIn = deposit.grantedIn
                )
            ).createdAllocation
            FindByStringId(created.toString())
        }

        return response
    }

    suspend fun rootAllocate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<RootAllocationRequestItem>
    ): List<FindByStringId> {
        val actor = actorAndProject.actor
        if (actor !is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        if (actor.principal.role != Role.ADMIN) {
            val relevantProviders = request.items.map { it.productCategory.provider }.toSet()
            // TODO(Dan): fragile?
            db.withSession { session ->
                val adminOf = session.sendPreparedStatement(
                    {
                        setParameter("providers", relevantProviders.toList())
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        select p.unique_name
                        from
                            provider.providers p
                            join provider.resource r on p.resource = r.id
                            join project.project_members pm on pm.project_id = r.project
                        where
                            p.unique_name = some(:providers::text[])
                            and pm.username = :username
                            and (
                                pm.role = 'PI'
                                or pm.role = 'ADMIN'
                            )
                    """
                ).rows.map { it.getString(0)!! }

                if (relevantProviders.any { it !in adminOf }) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }

        return request.items.map { deposit ->
            FindByStringId(
                processor.rootDeposit(
                    AccountingRequest.RootDeposit(
                        Actor.System,
                        deposit.owner.toProcessorOwner(),
                        deposit.productCategory,
                        deposit.quota,
                        startDate = deposit.start,
                        endDate = deposit.end,
                        forcedSync = deposit.forcedSync
                    )
                ).createdAllocation.toString()
            )
        }
    }

    suspend fun updateAllocation(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateAllocationV2RequestItem>,
    ) {
        request.items.forEach { update ->
            processor.update(
                AccountingRequest.Update(
                    //TODO(HENRIK) ADD TRANSACTIUO AND REASON
                    actorAndProject.actor,
                    update.allocationId.toIntOrNull() ?: return@forEach,
                    update.newQuota,
                    update.newStart,
                    update.newEnd,
                )
            )
        }
    }

    suspend fun browseWallets(
        actorAndProject: ActorAndProject,
        request: WalletBrowseRequest,
    ): PageV2<Wallet> {
        return db.withSession { session ->
            val itemsPerPage = request.normalize().itemsPerPage
            val rows = session.sendPreparedStatement(
                {
                    setParameter("user", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    setParameter("filter_type", request.filterType?.name)
                    setParameter("filter_empty", request.filterEmptyAllocations)
                    setParameter("next", request.next?.toLongOrNull())
                },
                """
                    select accounting.wallet_to_json(w, wo, array_agg(alloc), pc), w.id, pc.product_type, pc.category
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
                        ) and 
                        (
                            :next::bigint is null or
                            w.id > :next::bigint
                        )
                    group by w.*, wo.*, pc.*, pc.provider, pc.category, w.id, pc.product_type, pc.category
                    order by w.id
                    limit $itemsPerPage
                """,
                "Accounting Browse Wallets"
            ).rows

            val items = ArrayList<Wallet>()
            var lastId = 0L

            rows.forEach {
                var wallet = defaultMapper.decodeFromString(Wallet.serializer(), it.getString(0)!!)
                val productPriceUnit =
                    translateToProductPriceUnit(ProductType.valueOf(it.getString(2)!!), it.getString(3)!!)
                items.add(wallet.copy(unit = productPriceUnit))
                lastId = it.getLong(1)!!
            }

            val next = if (items.size < itemsPerPage) null else lastId.toString()

            PageV2(itemsPerPage, items, next)
        }
    }

    suspend fun browseSubAllocations(
        actorAndProject: ActorAndProject,
        request: SubAllocationQuery,
        query: String? = null,
    ): PageV2<SubAllocationV2> {
        val owner =
            if (actorAndProject.project == null) actorAndProject.actor.safeUsername()
            else actorAndProject.project!!

        val hits = processor.browseSubAllocations(
            AccountingRequest.BrowseSubAllocations(actorAndProject.actor, owner, request.filterType, query)
        )

        return PageV2.of(hits.allocations)
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
                                    principal.dtype = 'PASSWORD' or
                                    principal.dtype = 'PERSON'
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

    suspend fun retrieveProviderAllocations(
        actorAndProject: ActorAndProject,
        request: WalletsRetrieveProviderSummaryRequest,
    ): PageV2<ProviderWalletSummaryV2> {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        return processor.retrieveProviderAllocations(
            AccountingRequest.RetrieveProviderAllocations(
                actorAndProject.actor,
                providerId,
                request.filterOwnerId,
                request.filterOwnerIsProject,
                request.filterCategory,
                request.normalize(),
            )
        ).page
    }

    suspend fun retrieveChartsV2(
        allocator: BinaryAllocator,
        actorAndProject: ActorAndProject,
        request: VisualizationV2.RetrieveCharts.Request,
    ): Charts = with(allocator) {
        if (request.end - request.start > (365 * 15).days.inWholeMilliseconds) {
            throw RPCException("You cannot request data for more than 15 years", HttpStatusCode.BadRequest)
        }

        val categories = ArrayList<ProductCategoryB>()
        val allocations = ArrayList<WalletAllocationB>()
        val productCategoryIdToIndex = HashMap<Long, Int>()
        val usageOverTimeCharts = HashMap<Long, UsageOverTime>()
        val breakdownByProjectCharts = HashMap<Long, BreakdownByProject>()

        var emptyUsageChart: UsageOverTime? = null
        fun createEmptyUsageChart(): UsageOverTime {
            if (emptyUsageChart != null) return emptyUsageChart!!
            emptyUsageChart = UsageOverTime(BinaryTypeList.create(UsageOverTimeDataPoint, allocator, 0))
            return emptyUsageChart!!
        }

        var emptyBreakdownChart: BreakdownByProject? = null
        fun createEmptyBreakdownChart(): BreakdownByProject {
            if (emptyBreakdownChart != null) return emptyBreakdownChart!!
            emptyBreakdownChart =
                BreakdownByProject(BinaryTypeList.Companion.create(BreakdownByProjectPoint, allocator, 0))
            return emptyBreakdownChart!!
        }

        fun assembleResult(): Charts {
            val result = allocator.allocate(Charts)

            val charts = ArrayList<ChartsForCategory>()
            run {
                val allKeys = usageOverTimeCharts.keys + breakdownByProjectCharts.keys
                for (key in allKeys) {
                    val categoryIdx = productCategoryIdToIndex[key] ?: continue
                    val usageOverTime = usageOverTimeCharts[key] ?: createEmptyUsageChart()
                    val breakdownByProject = breakdownByProjectCharts[key] ?: createEmptyBreakdownChart()

                    charts.add(
                        ChartsForCategory(
                            categoryIdx,
                            usageOverTime,
                            breakdownByProject
                        )
                    )
                }
            }

            result.charts = BinaryTypeList.Companion.create(ChartsForCategory, allocator, charts)
            result.allocations = BinaryTypeList.Companion.create(WalletAllocationB, allocator, allocations)
            result.categories = BinaryTypeList.Companion.create(ProductCategoryB, allocator, categories)
            return result
        }

        val timeRange = (request.start)..(request.end)
        if (timeRange.isEmpty()) return assembleResult()

        val allWallets = processor
            .retrieveWalletsInternal(
                AccountingRequest.RetrieveWalletsInternal(
                    actorAndProject.actor,
                    actorAndProject.project ?: actorAndProject.actor.username,
                )
            )
            .wallets
            .mapNotNull { w ->
                val newAllocations = w.allocations.filter { a ->
                    val period = (a.startDate)..(a.endDate)
                    timeRange.overlaps(period)
                }

                if (newAllocations.isEmpty()) return@mapNotNull null
                if (w.paysFor.freeToUse) return@mapNotNull null

                w.copy(allocations = newAllocations)
            }

        for ((walletIndex, wallet) in allWallets.withIndex()) {
            val c = wallet.paysFor
            categories.add(c.toBinary(allocator))

            for (alloc in wallet.allocations) {
                allocations.add(
                    WalletAllocationB(
                        id = alloc.id.toLong(),
                        usage = alloc.treeUsage ?: 0L,
                        localUsage = alloc.localUsage,
                        quota = alloc.quota,
                        startDate = alloc.startDate,
                        endDate = alloc.endDate,
                        categoryIndex = walletIndex
                    )
                )
            }
        }

        db.withSession { session ->
            val pcRows = session.sendPreparedStatement(
                {
                    allWallets.split {
                        into("names") { it.paysFor.name }
                        into("providers") { it.paysFor.provider }
                    }
                },
                """
                    with
                        needles as (
                            select
                                unnest(:names::text[]) as name,
                                unnest(:providers::text[]) as provider
                        )
                    select
                        pc.id,
                        pc.category,
                        pc.provider
                    from
                        needles n
                        join accounting.product_categories pc on
                            n.name = pc.category
                            and n.provider = pc.provider
                """
            ).rows

            for (row in pcRows) {
                val category = row.getString(1)!!
                val provider = row.getString(2)!!
                productCategoryIdToIndex[row.getLong(0)!!] = allWallets.indexOfFirst {
                    it.paysFor.name == category && it.paysFor.provider == provider
                }
            }

            run {
                // Usage over time
                val rows = session.sendPreparedStatement(
                    {
                        setParameter("allocation_ids", allWallets.flatMap { w ->
                            w.allocations.mapNotNull { it.id.toLongOrNull() }
                        })

                        setParameter("start", request.start)
                        setParameter("end", request.end)
                    },
                    """
                        select distinct w.category, tree_usage, quota, provider.timestamp_to_unix(sampled_at)::int8
                        from
                            accounting.wallet_allocations alloc
                            join accounting.wallets w on alloc.associated_wallet = w.id
                            join accounting.wallet_samples s on w.id = s.wallet_id
                        where
                            alloc.id = some(:allocation_ids::int8[])
                            and s.sampled_at >= to_timestamp(:start / 1000.0)
                            and s.sampled_at <= to_timestamp(:end / 1000.0)
                        order by w.category, provider.timestamp_to_unix(sampled_at)::int8;
                    """
                ).rows

                var currentProductCategory = -1L

                var dataPoints = ArrayList<UsageOverTimeDataPoint>()
                fun flushChart() {
                    if (currentProductCategory != -1L) {
                        val res = allocator.allocate(UsageOverTime)
                        res.data = BinaryTypeList.Companion.create(UsageOverTimeDataPoint, allocator, dataPoints)
                        usageOverTimeCharts[currentProductCategory] = res

                        dataPoints = ArrayList()
                    }
                }

                for (row in rows) {
                    val allocCategory = row.getLong(0)!!
                    val usage = row.getLong(1)!!
                    val quota = row.getLong(2)!!
                    val timestamp = row.getLong(3)!!

                    if (currentProductCategory != allocCategory) {
                        flushChart() // no-op if currentProductCategory = -1L
                        currentProductCategory = allocCategory
                    }

                    dataPoints.add(UsageOverTimeDataPoint(usage, quota, timestamp))
                }
                flushChart()
            }

            run {
                // Breakdown by project
                db.withSession { session ->
                    val rows = session.sendPreparedStatement(
                        {
                            setParameter("allocation_ids", allWallets.flatMap { w ->
                                w.allocations.mapNotNull { it.id.toLongOrNull() }
                            })

                            setParameter("start", request.start)
                            setParameter("end", request.end)
                        },
                        """
                            with
                                relevant_wallets as (
                                    select
                                        w.id,
                                        w.category,
                                        pc.accounting_frequency != 'ONCE' as is_periodic,
                                        nlevel(child.allocation_path) = nlevel(alloc.allocation_path) + 1 as is_child
                                    from
                                        accounting.wallet_allocations alloc
                                        join accounting.wallet_allocations child on
                                            alloc.allocation_path @> child.allocation_path
                                            and nlevel(child.allocation_path) <= nlevel(alloc.allocation_path) + 1
                                        join accounting.wallets w on child.associated_wallet = w.id
                                        join accounting.product_categories pc on w.category = pc.id
                                    where
                                        alloc.id = some(:allocation_ids::int8[])
                                ),
                                data_timestamps as (
                                    select
                                        w.id, w.category, w.is_periodic, w.is_child,
                                        min(s.sampled_at) as oldest_data_ts,
                                        max(s.sampled_at) as newest_data_ts
                                    from
                                        relevant_wallets w
                                        join accounting.wallet_samples s on w.id = s.wallet_id
                                    where
                                        s.sampled_at >= to_timestamp(:start / 1000.0)
                                        and s.sampled_at <= to_timestamp(:end / 1000.0)
                                    group by
                                        w.id, w.category, w.is_periodic, w.is_child
                                ),
                                with_usage as (
                                    select
                                        dts.id,
                                        dts.category,
                                        case
                                            when dts.is_periodic and dts.is_child then newest_sample.tree_usage - oldest_sample.tree_usage
                                            when dts.is_periodic and not dts.is_child then newest_sample.local_usage - oldest_sample.local_usage
                                            when not dts.is_periodic and dts.is_child then newest_sample.tree_usage
                                            when not dts.is_periodic and not dts.is_child then newest_sample.local_usage
                                        end as usage
                                    from
                                        data_timestamps dts
                                        join accounting.wallet_samples oldest_sample on
                                            dts.oldest_data_ts = oldest_sample.sampled_at
                                            and dts.id = oldest_sample.wallet_id
                                        join accounting.wallet_samples newest_sample on
                                            dts.newest_data_ts = newest_sample.sampled_at
                                            and dts.id = newest_sample.wallet_id
                                )
                            select
                                u.category,
                                p.id,
                                coalesce(p.title, wo.username),
                                u.usage
                            from
                                with_usage u
                                join accounting.wallets w on u.id = w.id
                                join accounting.wallet_owner wo on w.owned_by = wo.id
                                left join project.projects p on wo.project_id = p.id
                            order by category;
                        """,
                    ).rows

                    var currentCategory = -1L
                    var dataPoints = ArrayList<BreakdownByProjectPoint>()
                    fun flushChart() {
                        if (currentCategory != -1L) {
                            val res = allocator.allocate(BreakdownByProject)
                            res.data = BinaryTypeList.create(BreakdownByProjectPoint, allocator, dataPoints)
                            breakdownByProjectCharts[currentCategory] = res
                            dataPoints = ArrayList()
                        }
                    }

                    for (row in rows) {
                        val categoryId = row.getLong(0)!!
                        val projectId = row.getString(1)
                        val workspaceTitle = row.getString(2)!!
                        val usage = row.getLong(3)!!

                        if (categoryId != currentCategory) {
                            flushChart()
                            currentCategory = categoryId
                        }

                        dataPoints.add(
                            BreakdownByProjectPoint(
                                title = workspaceTitle,
                                projectId = projectId,
                                usage = usage,
                            )
                        )
                    }
                    flushChart()
                }
            }
        }

        return assembleResult()
    }

    private fun LongRange.overlaps(other: LongRange): Boolean {
        return first <= other.last && other.first <= last
    }

    data class TestDataObject(
        val projectId: String,
        val categoryName: String,
        val provider: String,
        val usage: Long,
    )

    suspend fun generateTestData(
        objects: List<TestDataObject>,
        spreadOverDays: Int = 3650,
        clusterCount: Int = 600,
    ) {
        require(devMode) { "devMode must be on" }

        val stepSize = 1000L * 60 * 10
        val duration = 1000L * 60 * 60 * 24 * spreadOverDays
        val stepCount = duration / stepSize
        val endOfPeriod = Time.now()
        val startOfPeriod = endOfPeriod - duration
        val charges = ArrayList<Pair<Long, AccountingRequest.Charge>>()

        Time.provider = StaticTimeProvider

        var before = 0
        for (obj in objects) {
            with(obj) {
                val allTimestamps = HashSet<Long>()

                for (cluster in LongArray(clusterCount) { Random.nextLong(stepCount - 10) }) {
                    for (i in 0 until 10) {
                        allTimestamps.add(stepSize * (cluster + i) + startOfPeriod)
                    }
                }

                val allWallets = processor.retrieveWalletsInternal(
                    AccountingRequest.RetrieveWalletsInternal(
                        Actor.System,
                        projectId,
                    )
                )

                val wallet =
                    allWallets.wallets.find { it.paysFor.name == categoryName && it.paysFor.provider == provider }
                        ?: error("no wallet in $projectId $categoryName $provider")
                val isMonotonic = wallet.paysFor.isPeriodic()

                if (isMonotonic) {
                    var charged = 0L

                    for ((index, timestamp) in allTimestamps.sorted().withIndex()) {
                        var usageInCharge = min(usage - charged, max(1, usage / allTimestamps.size))
                        if (index == allTimestamps.size - 1) {
                            usageInCharge = usage - charged
                        }
                        charged += usageInCharge
                        charges.add(
                            timestamp to AccountingRequest.Charge.DeltaCharge(
                                actor = Actor.System,
                                owner = projectId,
                                dryRun = false,
                                productCategory = ProductCategoryIdV2(categoryName, provider),
                                usage = usageInCharge,
                                description = ChargeDescription("test data", emptyList()),
                            )
                        )
                    }
                } else {
                    var currentUsage = Random.nextLong(usage)
                    var lastSwitch = 0L
                    var direction = 1
                    for ((index, timestamp) in allTimestamps.sorted().withIndex()) {
                        if (timestamp - lastSwitch > 1000L * 60 * 60 * 5) {
                            direction = if (Random.nextBoolean()) 1 else -1
                            lastSwitch = timestamp
                        }

                        val proposedDiff = (usage * (Random.nextDouble() * 0.03 * direction)).toLong()
                        currentUsage = max(0, min(usage, currentUsage + proposedDiff))
                        if (index == allTimestamps.size - 1) currentUsage = usage

                        charges.add(
                            timestamp to AccountingRequest.Charge.TotalCharge(
                                actor = Actor.System,
                                owner = projectId,
                                dryRun = false,
                                productCategory = ProductCategoryIdV2(categoryName, provider),
                                usage = currentUsage,
                                description = ChargeDescription("test data", emptyList()),
                            )
                        )
                    }
                }
            }

            before = charges.size
        }

        val sortedBy = charges.sortedBy { it.first }
        val syncEvery = 6.hours.inWholeMilliseconds
        for ((index, entry) in sortedBy.withIndex()) {
            val (timestamp, charge) = entry
            val prevTimestamp = if (index > 0) sortedBy[index - 1].first else 0L
            val timeSinceLastEntry = if (index == 0) 0L else timestamp - prevTimestamp

            val missingSyncs = (timeSinceLastEntry / syncEvery).toInt()
            repeat(missingSyncs) {
                StaticTimeProvider.time = prevTimestamp + (it * syncEvery)
                processor.sendRequest(AccountingRequest.Sync())
            }

            StaticTimeProvider.time = timestamp
            processor.sendRequest(charge)
        }
        processor.sendRequest(AccountingRequest.Sync())
        Time.provider = SystemTimeProvider
    }

    companion object : Loggable {
        override val log = logger()
        const val MAX_BUCKETS_FOR_CHART = 1000
    }
}
