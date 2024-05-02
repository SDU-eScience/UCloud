package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.days

class DataVisualization(
    private val db: DBContext,
    private val accountingSystem: AccountingSystem
) {

    private fun LongRange.overlaps(other: LongRange): Boolean {
        return first <= other.last && other.first <= last
    }

    suspend fun retrieveChartsV2(
        idCard: IdCard,
        request: VisualizationV2.RetrieveCharts.Request,
    ): ChartsAPI {
        if (request.end - request.start > (365 * 15).days.inWholeMilliseconds) {
            throw RPCException("You cannot request data for more than 15 years", HttpStatusCode.BadRequest)
        }

        val categories = ArrayList<ProductCategory>()
        val allocationGroups = ArrayList<AllocationGroupWithProductCategoryIndex>()
        val productCategoryIdToIndex = HashMap<Long, Int>()
        val usageOverTimeCharts = HashMap<Long, UsageOverTimeAPI>()
        val breakdownByProjectCharts = HashMap<Long, BreakdownByProjectAPI>()

        var emptyUsageChart: UsageOverTimeAPI? = null
        fun createEmptyUsageChart(): UsageOverTimeAPI {
            if (emptyUsageChart != null) return emptyUsageChart!!
            emptyUsageChart = UsageOverTimeAPI(emptyList())
            return emptyUsageChart!!
        }

        var emptyBreakdownChart: BreakdownByProjectAPI? = null
        fun createEmptyBreakdownChart(): BreakdownByProjectAPI {
            if (emptyBreakdownChart != null) return emptyBreakdownChart!!
            emptyBreakdownChart = BreakdownByProjectAPI(emptyList())
            return emptyBreakdownChart!!
        }

        fun assembleResult(): ChartsAPI {
            val charts = ArrayList<ChartsForCategoryAPI>()
            run {
                val allKeys = usageOverTimeCharts.keys + breakdownByProjectCharts.keys
                for (key in allKeys) {
                    val categoryIdx = productCategoryIdToIndex[key] ?: continue
                    val usageOverTime = usageOverTimeCharts[key] ?: createEmptyUsageChart()
                    val breakdownByProject = breakdownByProjectCharts[key] ?: createEmptyBreakdownChart()

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
                categories,
                allocationGroups,
                charts
            )
        }

        val timeRange = (request.start)..(request.end)
        if (timeRange.isEmpty()) return assembleResult()
        val allWallets = accountingSystem
            .sendRequest(
                AccountingRequest.BrowseWallets(
                    idCard
                )
            ).mapNotNull { wallet ->
                //get allocations within period
                val newGroups = wallet.allocationGroups.mapNotNull { ag ->
                    val newAlloc = mutableListOf<AllocationGroup.Alloc>()
                    ag.group.allocations.forEach {  a ->
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

        for ((walletIndex, wallet) in allWallets.withIndex()) {
            val c = wallet.paysFor
            categories.add(c)

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

            coroutineScope {
                val usageOverTime = launch {
                    // Usage over time
                    val rows = session.sendPreparedStatement(
                        {
                            setParameter("allocation_group_ids", allWallets.flatMap { w ->
                                w.allocationGroups.map { it.group.id }
                            })

                            setParameter("start", request.start)
                            setParameter("end", request.end)
                        },
                        """
                        with samples as (
                            select
                                s.wallet_id,
                                s.tree_usage,
                                s.quota,
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
                                     then s.tree_usage::double precision / 60.0
                                 when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR'
                                     then s.tree_usage::double precision / 60.0 / 60.0
                                 when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY'
                                     then s.tree_usage::double precision / 60.0 / 60.0 / 24.0
                            end tusage,
                            s.quota,
                            sample_time
                        from
                            accounting.wallets_v2 w
                            join accounting.product_categories pc on w.product_category = pc.id
                            join accounting.accounting_units au on pc.accounting_unit = au.id
                            join samples s on w.id = s.wallet_id
                        order by pc.id, s.sample_time
                    """
                    ).rows

                    var currentProductCategory = -1L

                    var dataPoints = ArrayList<UsageOverTimeDatePointAPI>()
                    fun flushChart() {
                        if (currentProductCategory != -1L) {
                            usageOverTimeCharts[currentProductCategory] = UsageOverTimeAPI(dataPoints.toList())
                            dataPoints = ArrayList()
                        }
                    }

                    for (row in rows) {
                        val allocCategory = row.getLong(0)!!
                        val usage = row.getDouble(1)!!
                        val quota = row.getLong(2)!!
                        val timestamp = row.getLong(3)!!

                        if (currentProductCategory != allocCategory) {
                            flushChart() // no-op if currentProductCategory = -1L
                            currentProductCategory = allocCategory
                        }

                        dataPoints.add(UsageOverTimeDatePointAPI(usage, quota, timestamp))
                    }
                    flushChart()
                }

                val breakDownByProject = launch {
                    // Breakdown by project
                    db.withSession { session ->
                        val rows = session.sendPreparedStatement(
                            {
                                setParameter("allocation_group_ids", allWallets.flatMap { w ->
                                    w.allocationGroups.map { it.group.id }
                                })

                                setParameter("start", request.start)
                                setParameter("end", request.end)
                            },
                            """
                            with
                                project_wallets as (
                                    select ag.associated_wallet as id
                                    from
                                        accounting.allocation_groups ag
                                    where
                                        ag.id = some(:allocation_group_ids::int8[])
                                ),
                                relevant_wallets as (
                                    select
                                        distinct wal.id,
                                        wal.product_category,
                                        pc.accounting_frequency != 'ONCE' as is_periodic
                                    from
                                        project_wallets pwal join
                                        accounting.allocation_groups child on child.parent_wallet = 11 join
                                        accounting.wallets_v2 wal on child.associated_wallet = wal.id join
                                        accounting.product_categories pc on wal.product_category = pc.id
                                    order by wal.id
                                ),
                                data_timestamps as (
                                    select
                                        w.id, w.product_category, w.is_periodic,
                                        min(s.sampled_at) as oldest_data_ts,
                                        max(s.sampled_at) as newest_data_ts
                                    from
                                        relevant_wallets w
                                        join accounting.wallet_samples_v2 s on w.id = s.wallet_id
                                    where
                                        s.sampled_at >= to_timestamp(:start / 1000.0)
                                        and s.sampled_at <= to_timestamp(:end / 1000.0)
                                    group by
                                        w.id, w.product_category, w.id, w.is_periodic, w.is_periodic
                                ),
                               with_usage as (
                                    select
                                        dts.id,
                                        dts.product_category,
                                        newest_sample.tree_usage,
                                        oldest_sample.tree_usage,
                                        case
                                            when dts.is_periodic then newest_sample.tree_usage - oldest_sample.tree_usage
                                            when not dts.is_periodic then newest_sample.tree_usage
                                        end as usage
                                    from
                                        data_timestamps dts
                                        join accounting.wallet_samples_v2 oldest_sample on
                                            dts.oldest_data_ts = oldest_sample.sampled_at
                                            and dts.id = oldest_sample.wallet_id
                                        join accounting.wallet_samples_v2 newest_sample on
                                            dts.newest_data_ts = newest_sample.sampled_at
                                            and dts.id = newest_sample.wallet_id
                                )
                            select
                                u.product_category,
                                p.id,
                                coalesce(p.title, wo.username),
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
                            val usage = row.getDouble(3)!!

                            if (categoryId != currentCategory) {
                                flushChart()
                                currentCategory = categoryId
                            }

                            dataPoints.add(
                                BreakdownByProjectPointAPI(
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
        }


        return assembleResult()
    }
}