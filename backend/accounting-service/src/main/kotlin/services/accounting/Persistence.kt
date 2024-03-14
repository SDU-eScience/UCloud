package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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

class RealAccountingPersistence(private val db: DBContext): AccountingPersistence {
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
                        reference
                    )
                    ownersByReference[reference] = owner
                    ownersById[id] = owner

                }
        }

        //Create allocations
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    with parentallocs as (
                        select walloc.id current_id, ltree2text(subltree(allocation_path, nlevel(allocation_path)-2, nlevel(allocation_path)-1)) parent_allocation_id
                        from accounting.wallet_allocations walloc where nlevel(allocation_path) > 1
                    ),
                    parentWallets as (
                        select current_id, associated_wallet
                        from accounting.wallet_allocations allocs join
                            parentallocs pa on pa.parent_allocation_id = allocs.id::text
                    )
                    select
                        walloc.id allocation_id,
                        walloc.associated_wallet associated_wallet,
                        pw.associated_wallet parent_wallet,
                        walloc.initial_balance quota,
                        extract(epoch from walloc.start_date)::bigint sdate,
                        extract(epoch from walloc.end_date)::bigint edate
                        walloc.initial_balance - walloc.local_balance usage,
                        walloc.retired,
                        walloc.retired_usage
                    from accounting.wallet_allocations walloc join
                        accounting.wallets wall on walloc.associated_wallet = wall.id join
                        accounting.wallet_owner wo  on wall.owned_by = wo.id join
                        accounting.product_categories pc on wall.category = pc.id left join
                        parentWallets pw on pw.current_id = walloc.id
                """.trimIndent()
            ).rows
                .forEach {
                    val id = it.getLong(0)!!.toInt()
                    val allocation = InternalAllocation(
                        id = id,
                        belongsTo = it.getLong(1)!!.toInt(),
                        parentWallet = it.getLong(2)?.toInt() ?: 0,
                        quota = it.getLong(4)!!,
                        start = it.getLong(5)!!,
                        end = it.getLong(6)!!,
                        retired = it.getBoolean(7)!!,
                        retiredUsage = it.getLong(8)!!
                    )
                    allocations[id] = allocation
                }
        }

        //Create Wallets
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    with usage as (
                        select 
                            associated_wallet,
                            initial_balance - local_balance usage,
                            owned_by,
                            pc.*,
                            au.*
                        from accounting.wallet_allocations alloc join 
                            accounting.wallets wal on alloc.associated_wallet = wal.id join
                            accounting.product_categories pc on pc.id = wal.category join
                            accounting.accounting_units au on au.id = pc.accounting_unit  
                    )
                    select 
                        associated_wallet, 
                        sum(usage), 
                        owned_by,
                        provider,
                        category,
                        product_type, 
                        name, 
                        name_plural, 
                        floating_point, 
                        display_frequency_suffix,
                        accounting_frequency,
                        free_to_use,
                        allow_sub_allocations
                    from usage 
                    group by 
                        associated_wallet, 
                        owned_by, 
                        provider, 
                        category,
                        product_type, 
                        name, 
                        name_plural, 
                        floating_point, 
                        display_frequency_suffix,
                        accounting_frequency,
                        free_to_use,
                        allow_sub_allocations
                    order by associated_wallet
                """.trimIndent()
            ).rows
                .forEach {row ->
                    val id = row.getLong(0)!!.toInt()
                    val usage = row.getLong(1)!!
                    val ownedBy = row.getInt(2)!!
                    val provider = row.getString(3)!!
                    val category = row.getString(4)!!
                    val productType = ProductType.valueOf(row.getString(5)!!)
                    val accountingUnit = AccountingUnit(
                        row.getString(6)!!,
                        row.getString(7)!!,
                        row.getBoolean(8)!!,
                        row.getBoolean(9)!!
                    )
                    val frequency = row.getString(10)!!
                    val freeToUse = row.getBoolean(11)!!
                    val allowSubAllocations = row.getBoolean(12)!!

                    val productCategory = ProductCategory(
                        name = category,
                        provider = provider,
                        accountingUnit = accountingUnit,
                        accountingFrequency = AccountingFrequency.fromValue(frequency),
                        productType = productType,
                        conversionTable = emptyList(),
                        freeToUse = freeToUse,
                        allowSubAllocations = allowSubAllocations
                    )

                    val walletAllocations = allocations
                        .filter { alloc ->
                            alloc.value.belongsTo == id
                        }.values.groupBy { it.parentWallet }


                    val allocationsByParent = HashMap<Int, InternalAllocationGroup>()

                    walletAllocations.forEach { (parent, allocs) ->
                        val allocationSet = HashMap<Int, Boolean>()
                        allocs.forEach {alloc ->
                            allocationSet[alloc.id] = alloc.retired
                        }
                        val group = InternalAllocationGroup(
                            treeUsage = 0L,
                            retiredTreeUsage = 0L,
                            earliestExpiration = allocs.minOf { it.end },
                            allocationSet
                        )
                        allocationsByParent[parent] = group
                    }



                    val totalActiveQuota = allocationsByParent.values.sumOf { it.totalActiveQuota()}

                    val givenAllocations = allocations.values
                        .filter { alloc ->
                            alloc.parentWallet == id
                        }

                    val totalAllocated = givenAllocations.sumOf { it.quota }

                    val retiredSubAllocations = givenAllocations.filter { it.retired }

                    val totalRetiredAllocated = retiredSubAllocations.sumOf { it.quota }

                    val wallet = InternalWallet(
                        id = id,
                        category = productCategory,
                        ownedBy = ownedBy,
                        localUsage = usage,
                        allocationsByParent = allocationsByParent,
                        childrenUsage = HashMap(), //TODO() NEED ALL WALLETS or DATA IN DB
                        localRetiredUsage = 0L, //TODO() NEED DATA CHANGE IN DB MIGRATION AND THEN READ
                        childrenRetiredUsage = HashMap(), //TODO() NEED ALL WALLETS OR DATA IN DB
                        excessUsage = if (usage > totalActiveQuota) usage - totalActiveQuota else 0L, //TODO() NEEDS TO USE TREEUSAGE?
                        totalAllocated = totalAllocated,
                        totalRetiredAllocated = totalRetiredAllocated,
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
                    select max(alloc.id) maxAllocation, max(wal.id) maxWallet, max(wo.id) maxOwner
                    from accounting.wallets wal join
                        accounting.wallet_allocations alloc on wal.id = alloc.associated_wallet join
                        accounting.wallet_owner wo on wal.owned_by = wo.id
                """.trimIndent()
            ).rows
                .singleOrNull() ?: throw RPCException("Cannot find ids???", HttpStatusCode.InternalServerError)
        }
        val maxAllocationID = idrow.getLong(0)?.toInt()
        val maxWalletID = idrow.getLong(1)?.toInt()
        val maxOwnerID = idrow.getLong(2)?.toInt()
        if (maxAllocationID != null) allocationsIdAccumulator.set(maxAllocationID + 1)
        if (maxWalletID != null) allocationsIdAccumulator.set(maxWalletID + 1)
        if (maxOwnerID != null) allocationsIdAccumulator.set(maxOwnerID + 1)

        //Post-load Calculation
        //TODO()Calculate tree usage

        //TODO()Calculate children usage
    }
    override suspend fun flushChanges() {
        TODO("Not yet implemented")
    }
}