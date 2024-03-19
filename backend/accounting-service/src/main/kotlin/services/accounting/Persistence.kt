package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.parameterList
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.client.request.forms.*
import kotlin.math.max

interface AccountingPersistence {
    suspend fun initialize()
    suspend fun flushChanges()
}

object FakeAccountingPersistence : AccountingPersistence {
    override suspend fun initialize() {

    }

    override suspend fun flushChanges() {

    }
}

class RealAccountingPersistence(private val db: DBContext, private val accountingSystem: AccountingSystem): AccountingPersistence {
    override suspend fun initialize() {
        //Create walletOwners
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select id, coalesce(username, project_id) from accounting.wallet_owner
                """.trimIndent()
            ).rows
                .forEach {
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
        }

        //Create allocations and groups
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select 
                        walloc.id, 
                        ag.associated_wallet,
                        ag.parent_wallet,
                        walloc.quota,
                        provider.timestamp_to_unix(walloc.allocation_start_time), 
                        provider.timestamp_to_unix(walloc.allocation_end_time), 
                        walloc.retired, 
                        walloc.retired_usage,
                        walloc.granted_in,
                        ag.id,
                        ag.tree_usage,
                        ag.retired_tree_usage
                    from accounting.allocation_groups ag join   
                        accounting.wallet_allocations_v2 walloc on ag.id = walloc.associated_allocation_group
                """.trimIndent()
            ).rows
                .forEach {
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
                        belongsTo = associatedWallet,
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
                        allocationGroups[groupId]!!.allocationSet[allocation.id] = allocation.retired
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
                        newGroup.allocationSet[allocation.id] = allocation.retired
                        allocationGroups[groupId] = newGroup
                    }
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
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select 
                        id, 
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
                    from accounting.product_categories pc on pc.id = wal.category join
                        accounting.accounting_units au on au.id = pc.accounting_unit  
                """.trimIndent()
            ).rows
                .forEach {
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
        }
        //Create Wallets
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select 
                        id,
                        wallet_owner,
                        product_category,
                        local_usage,
                        local_retired_usage,
                        excess_usage,
                        total_allocated,
                        total_retired_allocated
                    from 
                        accounting.wallets_v2
                """.trimIndent()
            ).rows
                .forEach { row ->
                    val id = row.getLong(0)!!.toInt()
                    val owner = row.getLong(1)!!.toInt()
                    val productCategory = productCategories[row.getLong(2)!!]
                    val localUsage = row.getLong(3)!!
                    val localRetiredUsage = row.getLong(4)!!
                    val excessUsage = row.getLong(5)!!
                    val totalAllocated = row.getLong(6)!!
                    val totalRetiredAllocated = row.getLong(7)!!

                    val allocGroups = allocationGroups.filter { it.value.associatedWallet == id }
                    val allocationByParent = HashMap<Int, InternalAllocationGroup>()
                    allocGroups.forEach { (_, allocationGroup) ->
                        allocationByParent[allocationGroup.parentWallet] = allocationGroup
                    }

                    val childrenAllocGroups = allocationGroups.filter { it.value.parentWallet == id  }
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
                        category = productCategory!!,
                        ownedBy = owner,
                        localUsage = localUsage,
                        allocationsByParent = allocationByParent,
                        childrenUsage = childrenUsage,
                        localRetiredUsage = localRetiredUsage,
                        childrenRetiredUsage = childrenRetiredUsage,
                        excessUsage = excessUsage,
                        totalAllocated = totalAllocated,
                        totalRetiredAllocated = totalRetiredAllocated,
                        isDirty = false
                    )

                    walletsById[id] = wallet
                }
        }

        walletsById.values
            .groupBy { it.ownedBy }
            .forEach { (owner, wallets) ->
                val walletArrayList = ArrayList(wallets)
                walletsByOwner[owner] = walletArrayList
            }

        //Handle IDs so ID counter is ready to new inserts
        val idrow = db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select max(alloc.id) maxAllocation, max(wal.id) maxWallet, max(wo.id) maxOwner, max(ag.id) maxGroup
                    from accounting.wallets_v2 wal join
                        accounting.wallet_allocations_v2 alloc on wal.id = alloc.associated_wallet join
                        accounting.wallet_owner wo on wal.owned_by = wo.id join
                        accounting.allocation_groups ag on wal.id = ag associated_wallet  
                """.trimIndent()
            ).rows
                .singleOrNull() ?: throw RPCException("Cannot find ids???", HttpStatusCode.InternalServerError)
        }
        val maxAllocationID = idrow.getLong(0)?.toInt()
        val maxWalletID = idrow.getLong(1)?.toInt()
        val maxOwnerID = idrow.getLong(2)?.toInt()
        val maxGroupId = idrow.getLong(3)?.toInt()
        if (maxAllocationID != null) allocationsIdAccumulator.set(maxAllocationID + 1)
        if (maxWalletID != null) walletsIdAccumulator.set(maxWalletID + 1)
        if (maxOwnerID != null) ownersIdAccumulator.set(maxOwnerID + 1)
        if (maxGroupId != null) allocationGroupIdAccumulator.set(maxGroupId + 1)

        data class Charge(
            val id: Long,
            val walletId: Long,
            val usage: Long
        )
        //Charge Intermediate table
        run {
            db.withSession { session ->
                val charges = session.sendPreparedStatement(
                    """
                        select id, wallet_id, usage
                        from accounting.intermediate_usage
                    """
                ).rows
                    .map {
                        Charge(
                            id = it.getLong(0)!!,
                            walletId = it.getLong(1)!!,
                            usage = it.getLong(2)!!
                        )
                    }
                charges.map { charge ->
                    accountingSystem.sendRequest(
                        AccountingRequest.SystemCharge(
                            walletId = charge.walletId,
                            amount = charge.usage
                        )
                    )
                }

                session.sendPreparedStatement(
                    {
                        setParameter("ids", charges.map { it.id })
                    },
                    """
                        delete from accounting.intermediate_usage where id in (select unnest(:ids::bigint[]));
                    """
                )


            }

        }
    }
    override suspend fun flushChanges() {
        val providerToIdMap = HashMap<Pair<String,String>, Long>()
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select id, category, provider from accounting.product_categories
                """.trimIndent()
            ).rows
                .forEach {
                    val key = Pair(it.getString(1)!!, it.getString(2)!!)
                    val id = it.getLong(0)!!
                    providerToIdMap[key] = id
                }
        }
        //Insert or update Owners
        db.withSession { session ->
            val dirtyOwners = ownersById.filter { it.value.dirty }

            session.sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val usernames by parameterList<String?>()
                    val projectIds by parameterList<String?>()
                    dirtyOwners.forEach { (id, owner) ->
                        ids.add(id.toLong())
                        if (owner.isProject()) {
                            usernames.add(null)
                            projectIds.add(owner.reference)
                        } else {
                            usernames.add(owner.reference)
                            projectIds.add(null)
                        }
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
                    on conflict(username, project_id) 
                    do nothing;
                """
            )
            dirtyOwners.forEach { id, owner ->
                ownersById[id]!!.dirty = false
            }

        }
        //Insert or update wallets
        db.withSession { session ->
            val dirtyWallets = walletsById.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val owners by parameterList<Long>()
                    val categories by parameterList<Long>()
                    val localUsages by parameterList<Long>()
                    val localRetiredUsages by parameterList<Long>()
                    val excessUsages by parameterList<Long>()
                    val totalAmountsAllocated by parameterList<Long>()
                    val totalRetiredAmounts by parameterList<Long>()

                    dirtyWallets.forEach { id, wallet ->
                        ids.add(id.toLong())
                        owners.add(wallet.ownedBy.toLong())
                        categories.add(providerToIdMap[Pair(wallet.category.name, wallet.category.provider)]!!)
                        localUsages.add(wallet.localUsage)
                        localRetiredUsages.add(wallet.localRetiredUsage)
                        excessUsages.add(wallet.excessUsage)
                        totalAmountsAllocated.add(wallet.totalAllocated)
                        totalRetiredAmounts.add(wallet.totalRetiredAllocated)
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
                            unnest(:total_retired_amounts::bigint[]) total_retired_amount
                    )
                    insert into accounting.wallets_v2 (
                        id,
                        wallet_owner,
                        product_category,
                        local_usage,
                        local_retired_usage,
                        excess_usage,
                        total_allocated,
                        total_retired_allocated
                    ) select id, owner, cateogry, local_usage, local_retired_usage, excess_usage, total_allocated, total_retired_amount
                    from data
                    on conflict (id) 
                    do update 
                    set
                        local_usage = excluded.local_usage,
                        local_retired_usage = excluded.local_retired_usage,
                        excess_usage = excluded.excess_usage,
                        total_allocated = excluded.total_allocated,
                        total_retired_amount = excluded.total_retired_amout
                """.trimIndent()
            )

            dirtyWallets.forEach { id, wallet ->
                walletsById[id]!!.isDirty = false
            }
        }
        //Insert or update Groups
        db.withSession { session ->
            val dirtyGroups = allocationGroups.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val parentsWallets by parameterList<Long>()
                    val associatedWallets by parameterList<Long>()
                    val treeUsages by parameterList<Long>()
                    val retiredTreeUsages by parameterList<Long>()

                    dirtyGroups.forEach { id, group ->
                        ids.add(id.toLong())
                        parentsWallets.add(group.parentWallet.toLong())
                        associatedWallets.add(group.associatedWallet.toLong())
                        treeUsages.add(group.treeUsage)
                        retiredTreeUsages.add(group.retiredTreeUsage)
                    }
                },
                """
                    with data as (
                        select 
                            unnest(:ids::bigint[]) id,
                            unnest(:owners::bigint[]) parent_wallet,
                            unnest(:categories::bigint[]) associated_wallet,
                            unnest(:local_usages::bigint[]) tree_usage,
                            unnest(:local_retired_usages::bigint[]) retired_tree_usage
                    )
                    insert into accounting.allocation_groups (
                        id,
                        parent_wallet,
                        associated_wallet,
                        tree_usage,
                        retired_tree_usage
                    ) select id, parent_wallet, associated_wallet, tree_usage, retired_tree_usage
                    from data
                    on conflict (id) 
                    do update 
                    set
                        tree_usage = excluded.tree_usage,
                        retired_tree_usage = excluded.retired_tree_usage
                """.trimIndent()
            )

            dirtyGroups.forEach { (groupId, _) ->
                allocationGroups[groupId]!!.isDirty = false
            }

        }

        //Insert or update allocations
        db.withSession { session ->
            val dirtyAllocations = allocations.filter { it.value.isDirty }

            session.sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val associatedAllocationGroups by parameterList<Long>()
                    val grants by parameterList<Long?>()
                    val quotas by parameterList<Long>()
                    val startTimes by parameterList<Long>()
                    val endTimes by parameterList<Long>()
                    val retires by parameterList<Boolean>()
                    val retired_usages by parameterList<Long>()

                    dirtyAllocations.forEach { id, alloc ->
                        ids.add(id.toLong())
                        val allocGroupId = allocationGroups.values.find { it.allocationSet.contains(id) } ?: error("Should exist")
                        associatedAllocationGroups.add(allocGroupId.id.toLong())
                        grants.add(alloc.grantedIn)
                        quotas.add(alloc.quota)
                        startTimes.add(alloc.start)
                        endTimes.add(alloc.end)
                        retires.add(alloc.retired)
                        retired_usages.add(alloc.retiredUsage)
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
                            unnest(:retires::bigint[]) retired,
                            unnest(:retired_usages::bigint[]) retired_usage
                    )
                    insert into accounting.allocation_groups (
                        id,
                        associated_allocation_group,
                        granted_in,
                        quota,
                        allocation_start_time,
                        allocation_end_time,
                        retired,
                        retired_usage
                    ) select id, alloc_group, granted_in, quota, start_time, end_time, retired, retired_usage
                    from data
                    on conflict (id) 
                    do update 
                    set
                        quota = excluded.quota,
                        start_time = excluded.start_time,
                        end_time = excluded.end_time,
                        retired = excluded.retired,
                        retired_usage = excluded.retired_usage
                """.trimIndent()
            )

            dirtyAllocations.forEach { (allocId, _) ->
                allocations[allocId]!!.isDirty = false
            }

        }
    }
}