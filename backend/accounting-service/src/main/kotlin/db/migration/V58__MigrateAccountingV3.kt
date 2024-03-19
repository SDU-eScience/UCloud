package db.migration

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.accounting.services.accounting.InternalAllocationGroup
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.Schema
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.toTimestamp
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.joda.time.DateTime
import java.sql.Struct
import java.time.LocalDateTime
import kotlin.math.max

@Schema("newaccounting")
@Suppress("ClassNaming", "NestedBlockDepth")
class V58__MigrateAccountingV3 : BaseJavaMigration() {

    data class Wallet(
        val id: Long,
        val ownedBy: Long,
        val category: Long
    )

    data class WalletAllocation(
        val id: Long,
        val associatedWallet: Long,
        val allocationPath: String,
        val balance: Long,
        val quota: Long,
        val localBalance: Long,
        val startDate: Long,
        val endDate: Long?,
        val grantedIn: Long?,
    )

    data class WalletOwner(
        val id: Long,
        val username: String?,
        val projectId: String?
    )

    override fun migrate(context: Context) {
        // NOTE(Dan): Given that this is no longer needed it will now silently ignore errors
        val connection = context.connection
        connection.autoCommit = false

        val wallets = HashMap<Long, Wallet>()
        val allocations = HashMap<Long, WalletAllocation>()
        val owners = HashMap<Long, WalletOwner>()

        connection.createStatement().use { statement ->
            //Wallet Owners Extraction
            statement.executeQuery("select id, username, project_id from accounting.wallet_owner;").use { row ->
                while (row.next()) {
                    val id = row.getLong(0)
                    val username = row.getString(1).takeIf { !row.wasNull() }
                    val projectId = row.getString(2).takeIf { !row.wasNull() }
                    owners[id] = WalletOwner(
                        id,
                        username,
                        projectId
                    )
                }
            }

            //Wallet Extraction
            statement.executeQuery("select id, owned_by, category from accounting.wallets;\n").use { row ->
                while (row.next()) {
                    val id = row.getLong(0)
                    val ownedBy = row.getLong(1)
                    val productCategory = row.getLong(2)
                    wallets[id] = Wallet(
                        id,
                        ownedBy,
                        productCategory
                    )
                }
            }

            //Wallet Allocation Extraction
            statement.executeQuery("""
                select 
                    id, 
                    allocation_path, 
                    associated_wallet, 
                    balance, 
                    initial_balance, 
                    local_balance, 
                    provider.timestamp_to_unix(start_date)::bigint, 
                    provider.timestamp_to_unix(end_date)::bigint, 
                    granted_in 
                from accounting.wallet_allocations
                    """).use { row ->
                while (row.next()) {
                    val id = row.getLong(0)
                    val allocationPath = row.getString(1)
                    val associatedWallet = row.getLong(2)
                    val balance = row.getLong(3)
                    val quota = row.getLong(4)
                    val localBalance = row.getLong(5)
                    val start = row.getLong(6)
                    val end = row.getLong(7).takeIf { !row.wasNull() }
                    val grantedIn = row.getLong(8).takeIf { !row.wasNull() }

                    allocations[id] = WalletAllocation(
                        id,
                        associatedWallet,
                        allocationPath,
                        balance,
                        quota,
                        localBalance,
                        start,
                        end,
                        grantedIn
                    )
                }
            }
        }

        data class NewAllocation(
            val id: Long,
            val belongsToAllocationGroup: Long,
            var quota: Long,
            var start: Long,
            var end: Long,
            var retired: Boolean,
            var retiredUsage: Long = 0L,
            val grantedIn: Long?
        )

        data class NewAllocationGroup(
            val id: Long,
            val belongsTo: Long,
            val parentWallet: Long?,
            var treeUsage: Long,
            var retiredTreeUsage: Long,
        )

        data class NewWallet(
            val id: Long,
            val ownedBy: Long,
            val category: Long,
            var localUsage: Long,
            var localRetiredUsage: Long,
            var excessUsage: Long,
            var totalAllocated: Long,
            var totalRetiredAllocated: Long,
        )

        val newAllocations = HashMap<Long, NewAllocation>()
        val newAllocationGroups = HashMap<Long, NewAllocationGroup>()
        val newWallets = HashMap<Long, NewWallet>()

        var idAccumulator = 1L

        wallets.values.forEach { wallet ->
            val new = NewWallet(
                wallet.id,
                wallet.ownedBy,
                wallet.category,
                localUsage = 0L,
                localRetiredUsage = 0,
                excessUsage = 0,
                totalAllocated = 0,
                totalRetiredAllocated = 0
            )
            newWallets[new.id] = new
        }

        allocations.values.forEach { alloc ->
            val allocationPathParts = alloc.allocationPath.split(".")
            val parent = if (allocationPathParts.size <= 1) {
                null
            } else {
                allocations[allocationPathParts[allocationPathParts.size-2].toLong()]!!.associatedWallet
            }
            val associatedWallet = alloc.associatedWallet

            val allocGroup = newAllocationGroups.values.find { it.belongsTo == associatedWallet && it.parentWallet == parent }
                ?: run {
                    val new = NewAllocationGroup(
                        idAccumulator++,
                        associatedWallet,
                        parent,
                        treeUsage = 0,
                        retiredTreeUsage = 0
                    )
                    newAllocationGroups[new.id] = new
                    new
                }


            if ((alloc.endDate ?: Long.MAX_VALUE) >= Time.now() ) {
                val newAllocation = NewAllocation(
                    alloc.id,
                    allocGroup.id,
                    alloc.quota,
                    alloc.startDate,
                    alloc.endDate ?: LocalDateTime.of(2024, 12, 31, 23, 59, 59).toTimestamp(),
                    retired = false,
                    retiredUsage = 0,
                    alloc.grantedIn
                )
                newAllocations[newAllocation.id] = newAllocation
            }
        }

        //Save current values
        run {
            val walletIds = ArrayList<Long>()
            val usage = ArrayList<Long>()

            newWallets.values.forEach { wallet ->
                val childGroups = newAllocationGroups.filter { it.value.parentWallet == wallet.id }
                val allocationsBelongingToWallet =
                    newAllocations.values.filter { it.belongsToAllocationGroup in childGroups }

                wallet.totalAllocated = allocationsBelongingToWallet.sumOf { it.quota }

                walletIds.add(wallet.id)
                usage.add(allocationsBelongingToWallet.map { allocations[it.id]!! }
                    .sumOf { max(0, it.quota - it.localBalance) })

            }


            connection.prepareStatement(
                """
                with data as (
                    select unnest(?::bigint[]) wallet_id, unnest(?::bigint[]) usage
                )
                    insert into accounting.intermediate_usage(wallet_id, usage) 
                    select wallet_id, usage
                    from data
            """
            ).apply {
                setArray(1, connection.createArrayOf("bigint", walletIds.toArray()))
                setArray(2, connection.createArrayOf("bigint", usage.toArray()))

            }.executeUpdate()

        }

        // wallets
        run {
            val walletIds = ArrayList(newWallets.keys.toList())
            val walletOwners = walletIds.map { newWallets[it]!!.ownedBy }
            val productCategory = walletIds.map { newWallets[it]!!.category }
            val localUsage = walletIds.map { newWallets[it]!!.localUsage }
            val localRetiredUsage = walletIds.map { newWallets[it]!!.localRetiredUsage }
            val excessUsage = walletIds.map { newWallets[it]!!.excessUsage }
            val totalAllocated = walletIds.map { newWallets[it]!!.totalAllocated }
            val totalRetiredAllocated = walletIds.map { newWallets[it]!!.totalRetiredAllocated }

            connection.prepareStatement(
                """
                with data as (
                    select 
                        unnest(?::bigint[]) id, 
                        unnest(?::bigint[]) wallet_owner, 
                        unnest(?::bigint[]) product_category, 
                        unnest(?::bigint[]) local_usage,
                        unnest(?::bigint[]) local_retired_usage, 
                        unnest(?::bigint[]) excess_usage, 
                        unnest(?::bigint[]) total_allocated, 
                        unnest(?::bigint[]) total_retired_allocated
                )
                    insert into accounting.wallets_v2(
                        id, 
                        wallet_owner, 
                        product_category, 
                        local_usage, 
                        local_retired_usage, 
                        excess_usage, 
                        total_allocated, 
                        total_retired_allocated
                    ) 
                    select id, wallet_owner, product_category, local_usage, local_retired_usage, excess_usage, total_allocated, total_retired_allocated
                    from data
            """
            ).apply {
                setArray(1, connection.createArrayOf("bigint", walletIds.toArray()))
                setArray(2, connection.createArrayOf("bigint", walletOwners.toTypedArray()))
                setArray(3, connection.createArrayOf("bigint", productCategory.toTypedArray()))
                setArray(4, connection.createArrayOf("bigint", localUsage.toTypedArray()))
                setArray(5, connection.createArrayOf("bigint", localRetiredUsage.toTypedArray()))
                setArray(6, connection.createArrayOf("bigint", excessUsage.toTypedArray()))
                setArray(7, connection.createArrayOf("bigint", totalAllocated.toTypedArray()))
                setArray(8, connection.createArrayOf("bigint", totalRetiredAllocated.toTypedArray()))

            }.executeUpdate()

        }

        //Allocation groups
        run {
            val allocationGroupIds = ArrayList(newAllocationGroups.keys.toList())
            val parentWallets = allocationGroupIds.map { newAllocationGroups[it]!!.parentWallet }
            val associatedWallets = allocationGroupIds.map { newAllocationGroups[it]!!.belongsTo }
            val treeUsages = allocationGroupIds.map { newAllocationGroups[it]!!.treeUsage }
            val retiredTreeUsages = allocationGroupIds.map { newAllocationGroups[it]!!.retiredTreeUsage }

            connection.prepareStatement(
                """
                with data as (
                    select 
                        unnest(?::bigint[]) id, 
                        unnest(?::bigint[]) parent_wallet,
                        unnest(?::bigint[]) associated_wallet,
                        unnest(?::bigint[]) tree_usage,
                        unnest(?::bigint[]) retired_tree_usage
                )
                    insert into accounting.allocation_groups(id, parent_wallet, associated_wallet, tree_usage, retired_tree_usage) 
                    select id, parent_wallet, associated_wallet, tree_usage, retired_tree_usage
                    from data
            """
            ).apply {
                setArray(1, connection.createArrayOf("bigint", allocationGroupIds.toArray()))
                setArray(2, connection.createArrayOf("bigint", parentWallets.toTypedArray()))
                setArray(3, connection.createArrayOf("bigint", associatedWallets.toTypedArray()))
                setArray(4, connection.createArrayOf("bigint", treeUsages.toTypedArray()))
                setArray(5, connection.createArrayOf("bigint", retiredTreeUsages.toTypedArray()))
            }.executeUpdate()
        }

        //Allocations
        run {
            val allocations = newAllocationGroups.keys.toList()
            val associated_allocation_group = allocations.map { newAllocations[it]!!.belongsToAllocationGroup }
            val granted_in = allocations.map { newAllocations[it]!!.grantedIn }
            val quota = allocations.map { newAllocations[it]!!.quota }
            val allocation_start_time = allocations.map { newAllocations[it]!!.start }
            val allocation_end_time = allocations.map { newAllocations[it]!!.end }
            val retired = allocations.map { newAllocations[it]!!.retired }
            val retired_usage = allocations.map { newAllocations[it]!!.retiredUsage }

            connection.prepareStatement(
                """
                with data as (
                    select 
                        unnest(?::bigint[]) id, 
                        unnest(?::bigint[]) associated_allocation_group,
                        unnest(?::bigint[]) granted_in,
                        unnest(?::bigint[]) quota,
                        unnest(?::bigint[]) allocation_start_time,
                        unnest(?::bigint[]) allocation_end_time,
                        unnest(?::bigint[]) retired,
                        unnest(?::bigint[]) retired_usage

                )
                    insert into accounting.wallet_allocations_v2(id, associated_allocation_group, granted_in, quota, allocation_start_time, allocation_end_time, retired, retired_usage) 
                    select id, associated_allocation_group, granted_in, quota, allocation_start_time, allocation_end_time, retired, retired_usage
                    from data
            """
            ).apply {
                setArray(1, connection.createArrayOf("bigint", allocations.toTypedArray()))
                setArray(2, connection.createArrayOf("bigint", associated_allocation_group.toTypedArray()))
                setArray(3, connection.createArrayOf("bigint", granted_in.toTypedArray()))
                setArray(4, connection.createArrayOf("bigint", quota.toTypedArray()))
                setArray(5, connection.createArrayOf("bigint", allocation_start_time.toTypedArray()))
                setArray(6, connection.createArrayOf("bigint", allocation_end_time.toTypedArray()))
                setArray(7, connection.createArrayOf("bool", retired.toTypedArray()))
                setArray(8, connection.createArrayOf("bigint", retired_usage.toTypedArray()))
            }.executeUpdate()
        }

        //Set sequence values
        run {
            val maxAllocationIdFound = newAllocations.keys.max()
            val maxGroupIdFound = newAllocationGroups.keys.max()
            val maxWalletIdFound = newWallets.keys.max()

            connection.prepareStatement(
                """
                    select setval('accounting.wallet_allocations_v2_id', ?, true);
                    select setval('accounting.allocation_groups_id', ?, true);
                    select setval('accounting.wallets_v2_id', ?, true);
                """
            ).apply {
                setLong(1, maxAllocationIdFound)
                setLong(2, maxGroupIdFound)
                setLong(3, maxWalletIdFound)
            }
        }
    }
}
