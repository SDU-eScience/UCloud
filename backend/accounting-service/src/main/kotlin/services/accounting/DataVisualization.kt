package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.*
import kotlin.collections.set
import kotlin.time.Duration.Companion.days

class DataVisualization(
    private val db: DBContext,
    private val accountingSystem: AccountingSystem,
    private val idCardService: IdCardService
) {
    private fun LongRange.overlaps(other: LongRange): Boolean {
        return first <= other.last && other.first <= last
    }

    suspend fun retrieveChartsV2(
        idCard: IdCard,
        request: VisualizationV2.RetrieveCharts.Request,
    ): ChartsAPI {
        if (request.end < request.start) {
            throw RPCException("End date cannot be before start date", HttpStatusCode.BadRequest)
        }
        if (request.end - request.start > (365 * 15).days.inWholeMilliseconds) {
            throw RPCException("You cannot request data for more than 15 years", HttpStatusCode.BadRequest)
        }

        val categories = ArrayList<ProductCategory>()
        val allocationGroups = ArrayList<AllocationGroupWithProductCategoryIndex>()
        val productCategoryIdToIndex = HashMap<Long, Int>()
        var usageOverTimeCharts = HashMap<Long, UsageOverTimeAPI>()
        val breakdownByProjectCharts = HashMap<Long, BreakdownByProjectAPI>()
        val childrenUsageOverTimeCharts = HashMap<String, HashMap<Long, UsageOverTimeAPI>>()
        var usagePerUserData = UsagePerUserAPI(emptyList())

        val emptyUsageChart = UsageOverTimeAPI(emptyList())
        val emptyBreakdownChart = BreakdownByProjectAPI(emptyList())
        val emptyChildrenUsageOvertimeChart = HashMap<String, HashMap<Long, UsageOverTimeAPI>>()

        val projectId = if (idCard is IdCard.User) {
            if (idCard.activeProject == 0 ) {
                null
            } else {
                idCardService.lookupProjectInformation(idCard.activeProject)?.projectId
            }
        } else {
            null
        }

        val username = if (idCard is IdCard.User) {
            idCardService.lookupUid(idCard.uid)
        } else {
            null
        }

        fun assembleResult(): ChartsAPI {
            val charts = ArrayList<ChartsForCategoryAPI>()
            run {
                val allKeys = usageOverTimeCharts.keys + breakdownByProjectCharts.keys
                for (key in allKeys) {
                    val lookUpIndex = productCategoryIdToIndex.filterValues { it.toLong() == key }.keys
                    if (lookUpIndex.isEmpty()) {continue}
                    val categoryIdx = productCategoryIdToIndex[lookUpIndex.first()] ?: continue
                    val usageOverTime = usageOverTimeCharts[key] ?: emptyUsageChart
                    val breakdownByProject = breakdownByProjectCharts[key] ?: emptyBreakdownChart

                    charts.add(
                        ChartsForCategoryAPI(
                            categoryIdx,
                            usageOverTime,
                            breakdownByProject
                        )
                    )
                }
            }
            return ChartsAPI(
                categories = categories,
                allocGroups = allocationGroups,
                charts = charts,
                childrenUsageOverTime = emptyChildrenUsageOvertimeChart,
                usagePerUser = usagePerUserData
            )
        }

        val timeRange = (request.start)..(request.end)
        if (timeRange.isEmpty()) return assembleResult()
        val allWallets = accountingSystem
            .sendRequest(
                AccountingRequest.BrowseWallets(
                    idCard,
                    includeChildren = true
                )
            ).mapNotNull { wallet ->
                //get allocations within period
                val newGroups = wallet.allocationGroups.mapNotNull { ag ->
                    val newAlloc = mutableListOf<AllocationGroup.Alloc>()
                    ag.group.allocations.forEach { a ->
                        val period = (a.startDate)..(a.endDate)
                        if (timeRange.overlaps(period))
                            newAlloc.add(a)
                    }
                    if (newAlloc.isEmpty()) {
                        null
                    } else {
                        ag.copy(group = ag.group.copy(allocations = newAlloc))
                    }
                }

                if (newGroups.isEmpty()) return@mapNotNull null
                if (wallet.paysFor.freeToUse) return@mapNotNull null

                wallet.copy(allocationGroups = newGroups)
            }

        val childrenIds = mutableSetOf<String>()

        for ((walletIndex, wallet) in allWallets.withIndex()) {
            val c = wallet.paysFor
            categories.add(c)
            wallet.children?.forEach {
                val childId = it.child.projectId
                if (childId != null) {
                    childrenIds.add(childId)
                }
            }
            for (ag in wallet.allocationGroups) {
                allocationGroups.add(
                    AllocationGroupWithProductCategoryIndex(
                        ag.group,
                        walletIndex
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
        }

        coroutineScope {
           /* launch {
                db.withSession { session ->
                    childrenIds.forEach { childId ->
                        val relevantAllocationGroups = mutableSetOf<Int>()
                        allWallets.forEach { wallet ->
                            wallet.children?.forEach {
                                val child = it.child.projectId
                                if (child != null && child == childId) {
                                    relevantAllocationGroups.add(it.group.id)
                                }
                            }
                        }

                        childrenUsageOverTimeCharts[childId] = retrieveUsageOverTime(
                            session,
                            relevantAllocationGroups.toList(),
                            request.start,
                            request.end,
                            productCategoryIdToIndex
                        )
                    }
                }
            }*/

            launch {
                // Usage by users
                db.withSession { session ->
                    val rows = session.sendPreparedStatement(
                        {
                            setParameter("start", request.start)
                            setParameter("end", request.end)
                            setParameter("project_id", projectId)
                            setParameter("username", username.takeIf {
                                projectId == null
                            })
                        },
                        """
                        with
                            project_wallets as (
                                    select wal.id, wal.product_category, wal.wallet_owner
                                    from
                                        accounting.wallet_owner wo join
                                        accounting.wallets_v2 wal on wo.id = wal.wallet_owner
                                    where
                                        (
                                            wo.project_id = :project_id
                                            or wo.username = :username::text
                                        )
        
                                ),
                            workspaces_with_category as (
                                select
                                    distinct p.id as product_id,
                                    pc.category,
                                    pc.provider,
                                    wo.username,
                                    wo.project_id
                                from
                                    project_wallets pw join
                                    accounting.wallet_owner wo on pw.wallet_owner = wo.id join
                                    accounting.allocation_groups ag on ag.associated_wallet = pw.id join
                                    accounting.wallet_allocations_v2 alloc on ag.id = alloc.associated_allocation_group join
                                    accounting.product_categories pc on pc.id = pw.product_category join
                                    accounting.products p on pc.id = p.category
                                where
                                    pc.product_type = 'COMPUTE'
                                    and alloc.allocation_start_time <= to_timestamp(:end / 1000)
                                    and to_timestamp(:start / 1000) <= alloc.allocation_end_time
                                ),
                            jobs as (
                                select distinct
                                    terminal_update.created_at - running_update.created_at as run_time,
                                    running_update.created_at - r.created_at as queue_time,
                                    job.replicas,
                                    job.resource,
                                    r.created_at,
                                    r.created_by,
                                    wr.category, wr.provider,
                                    r.product
                                from
                                    workspaces_with_category wr
                                    join provider.resource r on
                                        (
                                            wr.project_id = r.project
                                            or (wr.username = r.created_by and r.project is null)
                                        )
                                        and wr.product_id = r.product
                                    join app_orchestrator.jobs job on r.id = job.resource
                                    join provider.resource_update terminal_update on r.id = terminal_update.resource
                                    join provider.resource_update running_update on r.id = running_update.resource
                                where
                                    r.created_at >= to_timestamp(:start / 1000)
                                    and r.created_at <= to_timestamp(:end / 1000)
                                    and (
                                        terminal_update.extra->>'state' = 'SUCCESS'
                                        or terminal_update.extra->>'state' = 'FAILURE'
                                        or terminal_update.extra->>'state' = 'EXPIRED'
                                    )
                                    and running_update.extra->>'state' = 'RUNNING'
                            ),
                            jobs_with_usage_factor as (
                                select
                                    j.*,
                                    case
                                        when pc.accounting_frequency = 'PERIODIC_MINUTE' then p.price::float8
                                        when pc.accounting_frequency = 'PERIODIC_HOUR' then p.price::float8 / 60
                                        when pc.accounting_frequency = 'PERIODIC_DAY' then p.price::float8 / (60 * 24)
                                    end * replicas as price_per_minute
                                from
                                    jobs j
                                    join accounting.products p on j.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                    join accounting.accounting_units unit on pc.accounting_unit = unit.id
                            )
                        select
                            j.created_by, j.category, j.provider,
                            round(sum((extract(epoch from run_time) / 60) * price_per_minute))::int8 as usage
                        from
                            jobs_with_usage_factor j
                        group by
                            j.created_by, j.category, j.provider
                        order by 
                            j.provider, j.category, usage desc,j.created_by;
                """, debug = true
                    ).rows

                    var dataPoints = ArrayList<UsagePerUserPointAPI>()

                    for (row in rows) {
                        val createdBy = row.getString(0)!!
                        val category = row.getString(1)!!
                        val provider = row.getString(2)!!
                        val usage = row.getLong(3)!!

                        val pc = categories.find { it.name == category && it.provider == provider } ?: continue

                        dataPoints.add(
                            UsagePerUserPointAPI(
                                createdBy,
                                pc,
                                usage
                            )
                        )
                    }

                    usagePerUserData.data = dataPoints

                }
            }

            launch {
                // Usage over time
                val myGroupIds = allWallets.flatMap { w -> w.allocationGroups.map { it.group.id } }
                usageOverTimeCharts = retrieveUsageOverTime(db, myGroupIds, request.start, request.end,
                    productCategoryIdToIndex)
            }

            launch {
                // Breakdown by project
                db.withSession { session ->
                    val rows = session.sendPreparedStatement(
                        {
                            setParameter("project_ids", childrenIds.toSet().toList())
                            setParameter("start", request.start)
                            setParameter("end", request.end)
                        },
                        """
                            with
                                relevant_wallets as (
                                    select
                                        distinct wal.id,
                                        wal.product_category,
                                        pc.accounting_frequency != 'ONCE' as is_periodic
                                    from
                                        accounting.wallets_v2 wal join
                                        accounting.wallet_owner own on wal.wallet_owner = own.id join
                                        accounting.product_categories pc on wal.product_category = pc.id
                                    where own.project_id = some(:project_ids::text[])
                                    order by wal.id
                                ),
                                samples as (
                                    select *
                                    from accounting.wallet_samples_v2 s join relevant_wallets w on s.wallet_id = w.id
                                    where
                                        sampled_at >= to_timestamp(:start / 1000.0)
                                        and sampled_at <= to_timestamp(:end / 1000.0)
                                ),
                                data_timestamps as (
                                    select
                                        w.id, w.product_category, w.is_periodic,
                                        min(s.sampled_at) as oldest_data_ts,
                                        max(s.sampled_at) as newest_data_ts
                                    from
                                        accounting.wallet_samples_v2 s join relevant_wallets w on s.wallet_id = w.id
                                    where
                                        sampled_at >= to_timestamp(:start / 1000.0)
                                        and sampled_at <= to_timestamp(:end / 1000.0)
                                    group by
                                        w.id, w.product_category, w.id, w.is_periodic, w.is_periodic
                                ),
                                with_usage as (
                                    select
                                        dts.id,
                                        dts.product_category,
                                        newest_sample.tree_usage newest,
                                        oldest_sample.tree_usage oldest,
                                        case
                                            when dts.is_periodic then newest_sample.tree_usage - oldest_sample.tree_usage
                                            when not dts.is_periodic then newest_sample.tree_usage
                                        end as usage
                                    from
                                        data_timestamps dts
                                        join samples oldest_sample on
                                            dts.oldest_data_ts = oldest_sample.sampled_at
                                            and dts.id = oldest_sample.wallet_id
                                        join samples newest_sample on
                                            dts.newest_data_ts = newest_sample.sampled_at
                                            and dts.id = newest_sample.wallet_id
                                )
                            select
                                u.product_category,
                                p.id,
                                coalesce(p.title, wo.username),
                                case
                                    when au.floating_point = true then u.newest / 1000000.0
                                    when au.floating_point = false and pc.accounting_frequency = 'ONCE' then u.newest::double precision
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE' then u.newest::double precision / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR' then u.newest::double precision / 60.0 / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY' then u.newest::double precision / 60.0 / 60.0 / 24.0
                                end tnewest,
                                case
                                    when au.floating_point = true then u.oldest / 1000000.0
                                    when au.floating_point = false and pc.accounting_frequency = 'ONCE' then u.oldest::double precision
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE' then u.oldest::double precision / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR' then u.oldest::double precision / 60.0 / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY' then u.oldest::double precision / 60.0 / 60.0 / 24.0
                                end toldest,
                                case
                                    when au.floating_point = true then u.usage / 1000000.0
                                    when au.floating_point = false and pc.accounting_frequency = 'ONCE' then u.usage::double precision
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE' then u.usage::double precision / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR' then u.usage::double precision / 60.0 / 60.0
                                    when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY' then u.usage::double precision / 60.0 / 60.0 / 24.0
                                end tusage
                            from
                                with_usage u
                                join accounting.wallets_v2 w on u.id = w.id
                                join accounting.wallet_owner wo on w.wallet_owner = wo.id
                                join accounting.product_categories pc on w.product_category = pc.id
                                join accounting.accounting_units au on au.id = pc.accounting_unit
                                left join project.projects p on wo.project_id = p.id
                            where usage > 0
                            order by product_category;
                        """,
                    ).rows
                    var currentCategory = -1L
                    var dataPoints = ArrayList<BreakdownByProjectPointAPI>()
                    fun flushChart() {
                        if (currentCategory != -1L) {
                            breakdownByProjectCharts[currentCategory] = BreakdownByProjectAPI(
                                data = dataPoints
                            )

                            dataPoints = ArrayList()
                        }
                    }

                    for (row in rows) {
                        val categoryId = row.getLong(0)!!
                        val projectId = row.getString(1)
                        val workspaceTitle = row.getString(2)!!
                        val newest = row.getDouble(3)!!
                        val oldest = row.getDouble(4)!!
                        val usage = row.getDouble(5)!!

                        if (categoryId != currentCategory) {
                            flushChart()
                            currentCategory = categoryId
                        }

                        dataPoints.add(
                            BreakdownByProjectPointAPI(
                                title = workspaceTitle,
                                projectId = projectId,
                                usage = usage,
                                newestPoint = newest,
                                oldestPoint = oldest,
                            )
                        )
                    }
                    flushChart()
                }
            }
        }
        return assembleResult()
    }

    private suspend fun retrieveUsageOverTime(
        ctx: DBContext,
        groupIds: List<Int>,
        start: Long,
        end: Long,
        productCategoryIdToIndex: HashMap<Long, Int>,
    ): HashMap<Long, UsageOverTimeAPI> {
        return ctx.withSession { session ->
            val rows = session.sendPreparedStatement(
                {
                    setParameter("allocation_group_ids", groupIds.toList())
                    setParameter("start", start)
                    setParameter("end", end)
                },
                """
                    with samples as (
                        select
                            s.wallet_id,
                            s.local_usage,
                            s.tree_usage,
                            s.retired_tree_usage,
                            s.retired_usage,
                            s.quota,
                            s.total_allocated,
                            provider.timestamp_to_unix(s.sampled_at)::int8 sample_time
                        from
                            accounting.allocation_groups ag join
                            accounting.wallet_samples_v2 s on ag.associated_wallet = s.wallet_id
                        where
                            ag.id = some (:allocation_group_ids::int8[])
                            and s.sampled_at >= to_timestamp(:start / 1000.0)
                            and s.sampled_at <= to_timestamp(:end / 1000.0)
                        order by s.wallet_id
                    )
                    select
                        distinct pc.id,
                        case
                             when au.floating_point = true then s.tree_usage / 1000000.0
                             when au.floating_point = false and pc.accounting_frequency = 'ONCE'
                                 then s.tree_usage::double precision
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE'
                                 then (s.tree_usage::double precision + s.retired_tree_usage::double precision) / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR'
                                 then (s.tree_usage::double precision + s.retired_tree_usage::double precision) / 60.0 / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY'
                                 then (s.tree_usage::double precision + s.retired_tree_usage::double precision) / 60.0 / 60.0 / 24.0
                        end tusage,
                        case
                             when au.floating_point = true then s.quota / 1000000.0
                             when au.floating_point = false and pc.accounting_frequency = 'ONCE'
                                 then s.quota::double precision
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE'
                                 then (s.quota::double precision) / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR'
                                 then (s.quota::double precision) / 60.0 / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY'
                                 then (s.quota::double precision) / 60.0 / 60.0 / 24.0
                        end normalized_quota,
                        sample_time,
                        w.id,
                        case
                             when au.floating_point = true then s.tree_usage / 1000000.0
                             when au.floating_point = false and pc.accounting_frequency = 'ONCE'
                                 then s.local_usage::double precision
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE'
                                 then (s.local_usage::double precision + s.retired_usage::double precision) / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR'
                                 then (s.local_usage::double precision + s.retired_usage::double precision) / 60.0 / 60.0
                             when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY'
                                 then (s.local_usage::double precision + s.retired_usage::double precision) / 60.0 / 60.0 / 24.0
                        end lusage,
                        s.total_allocated
                    from
                        accounting.wallets_v2 w
                        join accounting.product_categories pc on w.product_category = pc.id
                        join accounting.accounting_units au on pc.accounting_unit = au.id
                        join samples s on w.id = s.wallet_id
                    order by pc.id, s.sample_time desc 
                """
            ).rows

            val chartsByProduct = HashMap<Long, UsageOverTimeAPI>()

            var currentProductCategory = -1L
            var dataPoints = ArrayList<UsageOverTimeDatePointAPI>()

            fun flushChart() {
                if (currentProductCategory != -1L) {
                    val index = productCategoryIdToIndex[currentProductCategory]
                    if (index != null) {
                        chartsByProduct[index.toLong()] = UsageOverTimeAPI(dataPoints.toList())
                    }
                    dataPoints = ArrayList()
                }
            }

            for (row in rows) {
                if (currentProductCategory == -1L) {
                    currentProductCategory = row.getLong(0)!!
                }
                val allocCategory = row.getLong(0)!!
                val treeUsage = row.getDouble(1)!!
                val quota = row.getDouble(2)!!
                val timestamp = row.getLong(3)!!
                val walletId = row.getLong(4)!!
                val localUsage = row.getDouble(5)!!
                val totalAllocated = row.getLong(6)!!
                if (currentProductCategory != allocCategory) {
                    flushChart() // no-op if currentProductCategory = -1L
                    currentProductCategory = allocCategory
                }
                dataPoints.add(
                    UsageOverTimeDatePointAPI(
                        treeUsage,
                        quota,
                        timestamp,
                        localUsage,
                        totalAllocated
                    )
                )
            }
            flushChart()
            return@withSession chartsByProduct
        }
    }
}
