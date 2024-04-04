package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.messages.BinaryAllocator
import dk.sdu.cloud.messages.BinaryTypeList
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.db
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.idCards
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.productCache
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.serviceClient
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class StatisticsService {
    suspend fun retrieveStatistics(
        allocator: BinaryAllocator,
        actorAndProject: ActorAndProject,
        start: Long,
        end: Long
    ): JobStatistics = with(allocator) {
        val categories = ArrayList<ProductCategoryB>()
        val categoryLookupTable = HashMap<ProductCategoryIdV2, Int>()
        val potentialCategories = productCache.products()
        fun lookupCategory(category: ProductCategoryIdV2): Int {
            val cached = categoryLookupTable[category]
            if (cached != null) return cached
            val result = potentialCategories.find {
                it.category.name == category.name && it.category.provider == category.provider
            } ?: return -1

            categories.add(result.category.toBinary(allocator))
            categoryLookupTable[category] = categories.size - 1
            return categories.size - 1
        }

        val usageByUser = ArrayList<JobUsageByUser>()
        val mostUsedApplications = ArrayList<MostUsedApplications>()
        val jobSubmissionStatistics = ArrayList<JobSubmissionStatistics>()
        fun assembleResult(): JobStatistics {
            val res = allocate(JobStatistics)
            res.categories = BinaryTypeList.create(ProductCategoryB, allocator, categories)
            res.usageByUser = BinaryTypeList.create(JobUsageByUser, allocator, usageByUser)
            res.mostUsedApplications = BinaryTypeList.create(MostUsedApplications, allocator, mostUsedApplications)
            res.jobSubmissionStatistics = BinaryTypeList.create(JobSubmissionStatistics, allocator, jobSubmissionStatistics)
            return res
        }

        if (end < start) return assembleResult()

        val card = idCards.fetchIdCard(actorAndProject) // This enforces that the user is a member of the specified workspace
        if (card !is IdCard.User) return assembleResult()

        val descendants = AccountingV2.retrieveDescendants.call(
            AccountingV2.RetrieveDescendants.Request(actorAndProject.project!!),
            serviceClient
        ).orThrow()

        db.withSession { session ->
            // Usage by user
            run {
                val rows = session.sendPreparedStatement(
                    {
                        setParameter("start", start)
                        setParameter("end", end)
                        setParameter("project_ids", descendants.descendants)
                        setParameter("username", actorAndProject.actor.safeUsername().takeIf {
                            actorAndProject.project == null
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
                                            wo.project_id in (select unnest(:project_ids::text[]))
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
                                        wr.project_id = r.project
                                        or (wr.username = r.created_by and r.project is null)
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
                        order
                            by j.provider, j.category, j.created_by;
                    """
                ).rows

                var dataPoints = ArrayList<JobUsageByUserDataPoint>()
                var currentCategory: ProductCategoryIdV2? = null
                fun flushChart() {
                    val cat = currentCategory ?: return
                    usageByUser.add(JobUsageByUser(
                        lookupCategory(cat),
                        BinaryTypeList.create(JobUsageByUserDataPoint, allocator, dataPoints)
                    ))
                    dataPoints = ArrayList()
                }

                for (row in rows) {
                    val createdBy = row.getString(0)!!
                    val category = row.getString(1)!!
                    val provider = row.getString(2)!!
                    val usage = row.getLong(3)!!

                    val thisCategory = ProductCategoryIdV2(category, provider)
                    if (thisCategory != currentCategory) flushChart()
                    currentCategory = thisCategory

                    dataPoints.add(
                        JobUsageByUserDataPoint(
                            createdBy,
                            usage
                        )
                    )
                }
                flushChart()
            }

            // Most used applications
            run {
                val rows = session.sendPreparedStatement(
                    {
                        setParameter("start", start)
                        setParameter("end", end)
                        setParameter("project_ids", descendants.descendants)
                        setParameter("username", actorAndProject.actor.safeUsername().takeIf {
                            actorAndProject.project == null
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
                                            wo.project_id in (select unnest(:project_ids::text[]))
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
                            )
                        select
                            wr.category,
                            wr.provider,
                            coalesce(g.title, app.title),
                            count(r.id)::int4
                        from
                            workspaces_with_category wr
                            join provider.resource r on (
                                wr.project_id = r.project
                                or (wr.username = r.created_by and r.project is null)
                                ) and r.product = wr.product_id
                            join app_orchestrator.jobs job on r.id = job.resource
                            join app_store.applications app on
                                job.application_name = app.name
                                and job.application_version = app.version
                            left join app_store.application_groups g on app.group_id = g.id
                        where
                            r.created_at >= to_timestamp(:start / 1000)
                            and r.created_at <= to_timestamp(:end / 1000)
                        group by
                            wr.category, wr.provider, coalesce(g.title, app.title)
                        order by
                            wr.category, wr.provider, count(r.id) desc;                       
                    """
                ).rows

                var dataPoints = ArrayList<MostUsedApplicationsDataPoint>()
                var currentCategory: ProductCategoryIdV2? = null
                fun flushChart() {
                    val cat = currentCategory ?: return
                    mostUsedApplications.add(MostUsedApplications(
                        lookupCategory(cat),
                        BinaryTypeList.create(MostUsedApplicationsDataPoint, allocator, dataPoints)
                    ))
                    dataPoints = ArrayList()
                }

                for (row in rows) {
                    val category = row.getString(0)!!
                    val provider = row.getString(1)!!
                    val applicationTitle = row.getString(2)!!
                    val usage = row.getInt(3)!!
                    val thisCategory = ProductCategoryIdV2(category, provider)
                    if (thisCategory != currentCategory) flushChart()
                    currentCategory = thisCategory

                    dataPoints.add(
                        MostUsedApplicationsDataPoint(applicationTitle, usage)
                    )
                }
                flushChart()
            }

            // Job submission stats
            run {

                val rows = session.sendPreparedStatement(
                    {
                        setParameter("start", start)
                        setParameter("end", end)
                        setParameter("project_ids", descendants.descendants)
                        setParameter("username", actorAndProject.actor.safeUsername().takeIf {
                            actorAndProject.project == null
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
                                            wo.project_id in (select unnest(:project_ids::text[]))
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
                                    (
                                        (
                                            (
                                                (
                                                    -- Shift the day-of-week (dow) such that the weeks starts on a Monday
                                                    (6 + extract(dow from r.created_at)) % 7
                                                )
                                            ) * 4 -- Select the appropriate base slot based on the day-of-week (4 slots per day)
                                        ) +
                                        -- Find the slot of the day (each slot is 6 hours -> 4 slots per day)
                                        floor(extract(hour from r.created_at) / 6)
                                    ) as slot,
                                    wr.category, wr.provider
                                from
                                    workspaces_with_category wr
                                    join provider.resource r on (
                                        wr.project_id = r.project
                                        or (wr.username = r.created_by and r.project is null)
                                        ) and r.product = wr.product_id
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
                            )
                        select
                            category, provider, slot::int4,
                            count(run_time)::int4 as job_count,
                            (extract(epoch from avg(run_time)))::int4 as average_run_seconds,
                            (extract(epoch from avg(queue_time)))::int4 as average_queue_seconds
                        from
                            jobs
                        group by
                            category, provider, slot
                        order by
                            category, provider, slot;                       
                    """
                ).rows

                var dataPoints = ArrayList<JobSubmissionStatisticsDataPoint>()
                var currentCategory: ProductCategoryIdV2? = null
                fun flushChart() {
                    val cat = currentCategory ?: return
                    jobSubmissionStatistics.add(JobSubmissionStatistics(
                        lookupCategory(cat),
                        BinaryTypeList.create(JobSubmissionStatisticsDataPoint, allocator, dataPoints)
                    ))
                    dataPoints = ArrayList()
                }

                for (row in rows) {
                    val category = row.getString(0)!!
                    val provider = row.getString(1)!!
                    val slotOfWeek = row.getInt(2)!!
                    val jobCount = row.getInt(3)!!
                    val averageRunTimeInSeconds = row.getInt(4)!!
                    val averageQueueTimeInSeconds = row.getInt(5)!!

                    val hourOfDayStart = ((slotOfWeek % 4) * 6).toByte()
                    val hourOfDayEnd = (((slotOfWeek + 1) % 4) * 6).toByte()
                    val dayOfWeek = (slotOfWeek / 4).toByte()

                    val thisCategory = ProductCategoryIdV2(category, provider)
                    if (thisCategory != currentCategory) flushChart()
                    currentCategory = thisCategory

                    dataPoints.add(
                        JobSubmissionStatisticsDataPoint(
                            dayOfWeek,
                            hourOfDayStart,
                            hourOfDayEnd,
                            0,
                            jobCount,
                            averageRunTimeInSeconds,
                            averageQueueTimeInSeconds,
                        )
                    )
                }
                flushChart()
            }
        }


        return assembleResult()
    }
}
