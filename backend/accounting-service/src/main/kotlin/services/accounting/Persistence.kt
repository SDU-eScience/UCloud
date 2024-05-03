package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import kotlin.math.max

interface AccountingPersistence {
    suspend fun initialize()
    suspend fun flushChanges()
    suspend fun loadOldData(system: AccountingSystem)
}

object FakeAccountingPersistence : AccountingPersistence {
    override suspend fun initialize() {}
    override suspend fun flushChanges() {}
    override suspend fun loadOldData(system: AccountingSystem) {}
}

class RealAccountingPersistence(private val db: DBContext) : AccountingPersistence {
    private var nextSynchronization = 0L
    private var didChargeOldData = false
    private var lastSampling = 0L

    override suspend fun initialize() {
        val now = Time.now()
        db.withSession { session ->
            //Fetching last sample date
            session.sendPreparedStatement(
                {},
                """
                        select provider.timestamp_to_unix(max(sampled_at))::int8
                        from accounting.wallet_samples_v2
                        limit 1
                    """
            ).rows.forEach {
                val mostRecentSample = it.getLong(0) ?: 0L
                lastSampling = mostRecentSample
            }


            // Create walletOwners
            session.sendPreparedStatement(
                {},
                """
                    select
                        id,
                        coalesce(username, project_id)
                    from accounting.wallet_owner
                """
            ).rows.forEach {
                val id = it.getLong(0)!!.toInt()
                val reference = it.getString(1)!!

                val owner = InternalOwner(
                    id,
                    reference,
                    false
                )
                ownersByReference[reference] = owner
                ownersById[id] = owner
            }

            // Create allocations and groups
            session.sendPreparedStatement(
                {},
                """
                    select 
                        walloc.id, 
                        ag.associated_wallet,
                        ag.parent_wallet,
                        walloc.quota,
                        provider.timestamp_to_unix(walloc.allocation_start_time)::bigint, 
                        provider.timestamp_to_unix(walloc.allocation_end_time)::bigint, 
                        walloc.retired, 
                        walloc.retired_usage,
                        walloc.granted_in,
                        ag.id,
                        ag.tree_usage,
                        ag.retired_tree_usage
                    from
                        accounting.allocation_groups ag join   
                        accounting.wallet_allocations_v2 walloc on ag.id = walloc.associated_allocation_group
                """
            ).rows.forEach {
                val allocationId = it.getLong(0)!!.toInt()
                val associatedWallet = it.getLong(1)!!.toInt()
                val parentWallet = it.getLong(2)?.toInt() ?: 0
                val quota = it.getLong(3)!!
                val startTime = it.getLong(4)!!
                val endTime = it.getLong(5)!!
                val retired = it.getBoolean(6)!!
                val retiredUsage = it.getLong(7)!!
                val grantedIn = it.getLong(8)

                val allocation = InternalAllocation(
                    id = allocationId,
                    belongsToWallet = associatedWallet,
                    parentWallet = parentWallet,
                    quota = quota,
                    start = startTime,
                    end = endTime,
                    retired = retired,
                    retiredUsage = retiredUsage,
                    grantedIn = grantedIn,
                    isDirty = false
                )

                allocations[allocationId] = allocation

                val groupId = it.getLong(9)!!.toInt()
                val treeUsage = it.getLong(10)!!
                val retiredTreeUsage = it.getLong(11)!!

                val group = allocationGroups[groupId]
                if (group != null) {
                    allocationGroups[groupId]!!.allocationSet[allocation.id] =
                        allocation.isActive(now)
                } else {
                    val newGroup = InternalAllocationGroup(
                        id = groupId,
                        associatedWallet = associatedWallet,
                        parentWallet = parentWallet,
                        treeUsage = treeUsage,
                        retiredTreeUsage = retiredTreeUsage,
                        earliestExpiration = 0L,
                        allocationSet = HashMap(),
                        isDirty = false
                    )
                    newGroup.allocationSet[allocation.id] = allocation.isActive(now)
                    allocationGroups[groupId] = newGroup
                }
            }

            //Set earliestExpiration for each allocationGroup
            allocationGroups.forEach { (groupId, group) ->
                val allocationsIds = group.allocationSet.map { it.key }
                var earliestExpiration = Long.MAX_VALUE
                allocationsIds.forEach { id ->
                    val alloc = allocations[id] ?: error("Allocation disappeared???")
                    val endTime = alloc.end
                    if (endTime < earliestExpiration && endTime >= Time.now()) {
                        earliestExpiration = endTime
                    }
                }
                allocationGroups[groupId]!!.earliestExpiration = earliestExpiration
            }

            val productCategories = HashMap<Long, ProductCategory>()
            session.sendPreparedStatement(
                {},
                """
                    select 
                        pc.id, 
                        category, 
                        provider, 
                        product_type, 
                        name, 
                        name_plural, 
                        floating_point, 
                        display_frequency_suffix,
                        accounting_frequency,
                        free_to_use,
                        allow_sub_allocations
                    from
                        accounting.product_categories pc
                        join accounting.accounting_units au on au.id = pc.accounting_unit  
                """
            ).rows.forEach {
                val id = it.getLong(0)!!
                val productType = ProductType.valueOf(it.getString(3)!!)
                val accountingUnit = AccountingUnit(
                    it.getString(4)!!,
                    it.getString(5)!!,
                    it.getBoolean(6)!!,
                    it.getBoolean(7)!!
                )
                val accountingFrequency = AccountingFrequency.fromValue(it.getString(8)!!)
                val pc = ProductCategory(
                    name = it.getString(1)!!,
                    provider = it.getString(2)!!,
                    productType = productType,
                    accountingUnit = accountingUnit,
                    accountingFrequency = accountingFrequency,
                    freeToUse = it.getBoolean(9)!!,
                    allowSubAllocations = it.getBoolean(10)!!
                )
                productCategories[id] = pc
            }

            // Create Wallets
            session.sendPreparedStatement(
                {},
                """
                    select 
                        id,
                        wallet_owner,
                        product_category,
                        local_usage,
                        local_retired_usage,
                        excess_usage,
                        total_allocated,
                        total_retired_allocated,
                        was_locked,
                        provider.timestamp_to_unix(last_significant_update_at)::int8
                    from 
                        accounting.wallets_v2
                """
            ).rows.forEach { row ->
                val id = row.getLong(0)!!.toInt()
                val owner = row.getLong(1)!!.toInt()
                val productCategory = productCategories[row.getLong(2)!!]!!
                val localUsage = row.getLong(3)!!
                val localRetiredUsage = row.getLong(4)!!
                val excessUsage = row.getLong(5)!!
                val totalAllocated = row.getLong(6)!!
                val totalRetiredAllocated = row.getLong(7)!!
                val wasLocked = row.getBoolean(8)!!
                val lastSignificantUpdateAt = row.getLong(9)!!

                mostRecentSignificantUpdateByProvider[productCategory.provider] = max(
                    mostRecentSignificantUpdateByProvider[productCategory.provider] ?: 0L,
                    lastSignificantUpdateAt
                )

                val allocGroups = allocationGroups.filter { it.value.associatedWallet == id }
                val allocationByParent = HashMap<Int, InternalAllocationGroup>()
                allocGroups.forEach { (_, allocationGroup) ->
                    allocationByParent[allocationGroup.parentWallet] = allocationGroup
                }

                val childrenAllocGroups = allocationGroups.filter { it.value.parentWallet == id }
                val childrenRetiredUsage = HashMap<Int, Long>()
                val childrenUsage = HashMap<Int, Long>()

                childrenAllocGroups.forEach { (_, group) ->
                    val treeUsage = group.treeUsage
                    val retriedUsage = group.retiredTreeUsage
                    childrenUsage[group.associatedWallet] = treeUsage
                    childrenRetiredUsage[group.associatedWallet] = retriedUsage
                }
                val wallet = InternalWallet(
                    id = id,
                    category = productCategory,
                    ownedBy = owner,
                    localUsage = localUsage,
                    allocationsByParent = allocationByParent,
                    childrenUsage = childrenUsage,
                    localRetiredUsage = localRetiredUsage,
                    childrenRetiredUsage = childrenRetiredUsage,
                    excessUsage = excessUsage,
                    totalAllocated = totalAllocated,
                    totalRetiredAllocated = totalRetiredAllocated,
                    isDirty = false,
                    wasLocked = wasLocked,
                    lastSignificantUpdateAt = lastSignificantUpdateAt,
                )

                walletsById[id] = wallet
            }

            walletsById.values
                .groupBy { it.ownedBy }
                .forEach { (owner, wallets) ->
                    val walletArrayList = ArrayList(wallets)
                    walletsByOwner[owner] = walletArrayList
                }

            val scopedUsageRows = session.sendPreparedStatement(
                {},
                """
                    select key, usage
                    from accounting.scoped_usage
                """
            ).rows.associate { row ->
                val key = row.getString(0)!!
                val usage = row.getLong(1)!!

                key to usage
            }
            scopedUsage.putAll(scopedUsageRows)

            // Handle IDs so ID counter is ready to new inserts
            val idRow = session.sendPreparedStatement(
                {},
                """
                    select
                        (select max(id) from accounting.wallet_allocations_v2 alloc) as maxAllocation,
                        (select max(id) from accounting.wallets_v2 wal) as maxWallet,
                        (select max(id) from accounting.wallet_owner wo) as maxOwner,
                        (select max(id) from accounting.allocation_groups ag) as maxGroup
                """
            ).rows.singleOrNull() ?: throw RPCException("Cannot find ids???", HttpStatusCode.InternalServerError)
            val maxAllocationID = idRow.getLong(0)?.toInt()
            val maxWalletID = idRow.getLong(1)?.toInt()
            val maxOwnerID = idRow.getLong(2)?.toInt()
            val maxGroupId = idRow.getLong(3)?.toInt()
            if (maxAllocationID != null) allocationsIdAccumulator.set(maxAllocationID + 1)
            if (maxWalletID != null) walletsIdAccumulator.set(maxWalletID + 1)
            if (maxOwnerID != null) ownersIdAccumulator.set(maxOwnerID + 1)
            if (maxGroupId != null) allocationGroupIdAccumulator.set(maxGroupId + 1)


        }
    }

    override suspend fun loadOldData(system: AccountingSystem) {
        data class Charge(
            val id: Long,
            val walletId: Long,
            val usage: Long
        )

        db.withSession { session ->
            //Charge Intermediate table
            val charges = session.sendPreparedStatement(
                """
                    select id, wallet_id, usage
                    from accounting.intermediate_usage
                """
            ).rows.map {
                Charge(
                    id = it.getLong(0)!!,
                    walletId = it.getLong(1)!!,
                    usage = it.getLong(2)!!
                )
            }

            charges.map { charge ->
                system.sendRequestNoUnwrap(
                    AccountingRequest.SystemCharge(
                        walletId = charge.walletId,
                        amount = charge.usage,
                        isDelta = true
                    )
                )
            }

            didChargeOldData = true
        }
    }

    override suspend fun flushChanges() {
        val now = Time.now()
        if (now < nextSynchronization) return

        val providerToIdMap = HashMap<Pair<String, String>, Long>()
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select id, category, provider from accounting.product_categories
                """
            ).rows.forEach {
                val key = Pair(it.getString(1)!!, it.getString(2)!!)
                val id = it.getLong(0)!!
                providerToIdMap[key] = id
            }

            // Insert or update Owners
            val dirtyOwners = ownersById.filter { it.value.dirty }

            session.sendPreparedStatement(
                {
                    dirtyOwners.entries.split {
                        into("ids") { it.key.toLong() }
                        into("usernames") { (_, o) -> o.reference.takeIf { !o.isProject() }}
                        into("project_ids") { (_, o) -> o.reference.takeIf { o.isProject() }}
                    }
                },
                """
                    with data as (
                        select 
                            unnest(:ids::bigint[]) id,
                            unnest(:usernames::text[]) username,
                            unnest(:project_ids::text[]) project_id
                    )
                    insert into accounting.wallet_owner (id, username, project_id)
                    select id, username, project_id 
                    from data
                    on conflict
                    do nothing;
                """
            )
            dirtyOwners.forEach { (id, _) ->
                ownersById[id]!!.dirty = false
            }

            //Insert or update wallets
            val dirtyWallets = walletsById.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    dirtyWallets.entries.split {
                        into("ids") { it.key.toLong() }
                        into("owners") { (_, wallet) -> wallet.ownedBy.toLong() }
                        into("categories") { (_, wallet) -> providerToIdMap[Pair(wallet.category.name, wallet.category.provider)]!! }
                        into("local_usages") { (_, wallet) -> wallet.localUsage }
                        into("local_retired_usages") { (_, wallet) -> wallet.localRetiredUsage }
                        into("excess_usages") { (_, wallet) -> wallet.excessUsage }
                        into("total_amounts_allocated") { (_, wallet) -> wallet.totalAllocated }
                        into("total_retired_amounts") { (_, wallet) -> wallet.totalRetiredAllocated }
                        into("was_locked") { (_, wallet) -> wallet.wasLocked }
                        into("last_significant_update_at") { (_, wallet) -> wallet.lastSignificantUpdateAt }
                    }
                },
                """
                    with data as (
                        select 
                            unnest(:ids::bigint[]) id,
                            unnest(:owners::bigint[]) owner,
                            unnest(:categories::bigint[]) cateogry,
                            unnest(:local_usages::bigint[]) local_usage,
                            unnest(:local_retired_usages::bigint[]) local_retired_usage,
                            unnest(:excess_usages::bigint[]) excess_usage,
                            unnest(:total_amounts_allocated::bigint[]) total_allocated,
                            unnest(:total_retired_amounts::bigint[]) total_retired_allocated,
                            unnest(:was_locked::bool[]) was_locked,
                            to_timestamp(unnest(:last_significant_update_at::bigint[]) / 1000.0) last_significant_update_at
                    )
                    insert into accounting.wallets_v2(
                        id,
                        wallet_owner,
                        product_category,
                        local_usage,
                        local_retired_usage,
                        excess_usage,
                        total_allocated,
                        total_retired_allocated,
                        was_locked,
                        last_significant_update_at
                    )
                    select
                        id,
                        owner,
                        cateogry,
                        local_usage,
                        local_retired_usage,
                        excess_usage,
                        total_allocated,
                        total_retired_allocated,
                        was_locked,
                        last_significant_update_at
                    from data
                    on conflict (id) 
                    do update 
                    set
                        local_usage = excluded.local_usage,
                        local_retired_usage = excluded.local_retired_usage,
                        excess_usage = excluded.excess_usage,
                        total_allocated = excluded.total_allocated,
                        total_retired_allocated = excluded.total_retired_allocated,
                        was_locked = excluded.was_locked,
                        last_significant_update_at = excluded.last_significant_update_at
                """
            )

            dirtyWallets.forEach { (id, _) ->
                walletsById[id]!!.isDirty = false
            }

            //Insert or update Groups
            val dirtyGroups = allocationGroups.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    dirtyGroups.entries.split {
                        into("ids") { (id, _) -> id.toLong() }
                        into("parent_wallets") { (_, group) -> group.parentWallet.toLong().takeIf { it != 0L } }
                        into("associated_wallets") { (_, group) -> group.associatedWallet.toLong() }
                        into("tree_usages") { (_, group) -> group.treeUsage }
                        into("local_retired_usages") { (_, group) -> group.retiredTreeUsage }
                    }
                },
                """
                    with data as (
                        select 
                            unnest(:ids::bigint[]) id,
                            unnest(:parent_wallets::bigint[]) parent_wallet,
                            unnest(:associated_wallets::bigint[]) associated_wallet,
                            unnest(:tree_usages::bigint[]) tree_usage,
                            unnest(:local_retired_usages::bigint[]) retired_tree_usage
                    )
                    insert into accounting.allocation_groups (
                        id,
                        parent_wallet,
                        associated_wallet,
                        tree_usage,
                        retired_tree_usage
                    )
                    select
                        id,
                        parent_wallet,
                        associated_wallet,
                        tree_usage,
                        retired_tree_usage
                    from data
                    on conflict (id) 
                    do update 
                    set
                        tree_usage = excluded.tree_usage,
                        retired_tree_usage = excluded.retired_tree_usage
                """
            )

            dirtyGroups.forEach { (groupId, _) ->
                allocationGroups[groupId]!!.isDirty = false
            }

            //Insert or update allocations
            val dirtyAllocations = allocations.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    dirtyAllocations.entries.split {
                        into("ids") { (id, _) -> id.toLong() }
                        into("associated_allocation_groups") { (id, _) -> allocationGroups.values.first { it.allocationSet.contains(id) }.id }
                        into("grants") { (_, alloc) -> alloc.grantedIn }
                        into("quotas") { (_, alloc) -> alloc.quota }
                        into("start_times") { (_, alloc) -> alloc.start }
                        into("end_times") { (_, alloc) -> alloc.end }
                        into("retires") { (_, alloc) -> alloc.retired }
                        into("retired_usages") { (_, alloc) -> alloc.retiredUsage }
                    }
                },
                """
                    with data as (
                        select 
                            unnest(:ids::bigint[]) id,
                            unnest(:associated_allocation_groups::bigint[]) alloc_group,
                            unnest(:grants::bigint[]) granted_in,
                            unnest(:quotas::bigint[]) quota,
                            unnest(:start_times::bigint[]) start_time,
                            unnest(:end_times::bigint[]) end_time,
                            unnest(:retires::bool[]) retired,
                            unnest(:retired_usages::bigint[]) retired_usage
                    )
                    insert into accounting.wallet_allocations_v2(
                        id,
                        associated_allocation_group,
                        granted_in,
                        quota,
                        allocation_start_time,
                        allocation_end_time,
                        retired,
                        retired_usage
                    )
                    select
                        id,
                        alloc_group,
                        granted_in,
                        quota,
                        to_timestamp(start_time / 1000.0),
                        to_timestamp(end_time / 1000.0),
                        retired,
                        retired_usage
                    from data
                    on conflict (id) 
                    do update 
                    set
                        quota = excluded.quota,
                        allocation_start_time = excluded.allocation_start_time,
                        allocation_end_time = excluded.allocation_end_time,
                        retired = excluded.retired,
                        retired_usage = excluded.retired_usage
                """
            )

            dirtyAllocations.forEach { (allocId, _) ->
                allocations[allocId]!!.isDirty = false
            }

            //Sample wallets?
            val now = Time.now()
            val shouldSampleWallets = (now - lastSampling).absoluteValue > 6.hours.inWholeMilliseconds
            if (shouldSampleWallets) {
                data class AllocGroup(
                    val allocGroupId: Int,
                    val treeUsage: Long,
                    val quota: Long,
                )

                data class Sample(
                    val walletId: Int,
                    var localUsage: Long = 0L,
                    var excessUsage: Long = 0L,
                    var localUsageChange: Long = 0L,
                    var combinedTreeUsage: Long = 0L,
                    var totalQuota: Long = 0L,
                    val allocGroups: ArrayList<AllocGroup> = ArrayList(),
                )

                val samples = HashMap<Int, Sample>()

                walletsById.forEach { (walletId, wallet) ->

                    val sample = Sample(
                        walletId = walletId,
                        localUsage = wallet.localUsage,
                        excessUsage = wallet.excessUsage,
                    )

                    wallet.allocationsByParent.forEach { (parentWalletId, group) ->
                        var quota = 0L
                        group.allocationSet.forEach { allocId, isActive ->
                            if (isActive) {
                                val foundAlloc = allocations[allocId]
                                if (foundAlloc != null) {
                                    quota += foundAlloc.quota
                                }
                            }
                        }
                        sample.allocGroups.add(
                            AllocGroup(
                                group.id,
                                group.treeUsage,
                                quota
                            )
                        )
                    }

                    sample.combinedTreeUsage = sample.allocGroups.sumOf { it.treeUsage }
                    sample.totalQuota = sample.allocGroups.sumOf { it.quota }
                    samples[walletId] = sample
                }

                samples.entries.chunked(500).forEach { chunk ->
                    session.sendPreparedStatement(
                        {
                            setParameter("now", now)
                            val walletIds = ArrayList<Int>().also { setParameter("wallet_ids", it) }
                            val localUsages = ArrayList<Long>().also { setParameter("local_usages", it) }
                            val excessUsages = ArrayList<Long>().also { setParameter("excess_usages", it) }
                            val combinedTreeUsages = ArrayList<Long>().also { setParameter("combined_tree_usages", it) }
                            val totalQuota = ArrayList<Long>().also { setParameter("total_quotas", it) }

                            val allocGroupIds = ArrayList<Int>().also { setParameter("group_ids", it) }
                            val allocGroupQuotas = ArrayList<Long>().also { setParameter("group_quotas", it) }
                            val allocGroupTreeUsages = ArrayList<Long>().also { setParameter("group_usages", it) }

                            for ((walletId, sample) in chunk) {
                                for (group in sample.allocGroups) {
                                    allocGroupIds.add(group.allocGroupId)
                                    allocGroupQuotas.add(group.quota)
                                    allocGroupTreeUsages.add(group.treeUsage)
                                }

                                walletIds.add(walletId)
                                localUsages.add(sample.localUsage)
                                excessUsages.add(sample.excessUsage)
                                combinedTreeUsages.add(sample.combinedTreeUsage)
                                totalQuota.add(sample.totalQuota)
                            }
                        },
                        """
                            with 
                                alloc_groups as (
                                    select
                                        unnest(:wallet_ids::int[]) as wallet_id,
                                        unnest(:group_ids::int[]) as group_id,
                                        unnest(:group_quotas::int8[]) as group_quota,
                                        unnest(:group_usages::int8[]) as group_usage

                                ),
                                aggregated_groups as (
                                    select
                                        wallet_id,
                                        array_agg(group_id) as group_ids,
                                        array_agg(group_quota) as quotas,
                                        array_agg(group_usage) as usages
                                    from alloc_groups
                                    group by wallet_id
                                ),
                                wallets as (
                                    select
                                        unnest(:wallet_ids::int4[]) as wallet_id,
                                        unnest(:local_usages::int8[]) as local_usage,
                                        unnest(:excess_usages::int8[]) as excess_usage,
                                        unnest(:combined_tree_usages::int8[]) as tree_usage,
                                        unnest(:total_quotas::int8[]) as quota        
                                ),
                                combined as (
                                    select
                                        to_timestamp(:now / 1000.0) t, w.wallet_id, w.local_usage, w.excess_usage, w.tree_usage, w.quota,
                                        coalesce(gr.group_ids, array[]::int4[]) as gr_id,
                                        coalesce(gr.quotas, array[]::int8[]) as gr_quota,
                                        coalesce(gr.usages, array[]::int8[]) as gr_usage
                                    from
                                        wallets w 
                                        left join aggregated_groups gr on w.wallet_id = gr.wallet_id
                                )
                                insert into accounting.wallet_samples_v2
                                    (sampled_at, wallet_id, quota, local_usage, excess_usage, tree_usage, 
                                    group_ids, tree_usage_by_group, quota_by_group)
                                select t, wallet_id, quota, local_usage, excess_usage, tree_usage,
                                    gr_id, gr_usage, gr_quota
                                from combined
                        """.trimIndent()
                    )
                }
                lastSampling = Time.now()
            }

            val scopedKeys = ArrayList<String>()
            val scopedValues = ArrayList<Long>()
            for (key in scopedDirty.keys) {
                val usage = scopedUsage[key] ?: continue
                scopedKeys.add(key)
                scopedValues.add(usage)
            }

            if (scopedKeys.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("keys", scopedKeys)
                        setParameter("values", scopedValues)
                    },
                    """
                        with data as (
                            select
                                unnest(:keys::text[]) as key,
                                unnest(:values::int8[]) as value
                        )
                        insert into accounting.scoped_usage(key, usage)
                        select key, value
                        from data
                        on conflict (key) do update set usage = excluded.usage
                    """
                )
            }

            if (didLoadUnsynchronizedGrants.get()) {
                session.sendPreparedStatement(
                    {},
                    """
                        update "grant".applications
                        set synchronized = true
                        where overall_state = 'APPROVED'
                    """
                )
            }

            if (didChargeOldData) {
                didChargeOldData = false

                session.sendPreparedStatement(
                    {},
                    """
                        delete from accounting.intermediate_usage where true;
                    """
                )
            }
        }

        nextSynchronization = now + 30_000
    }
}