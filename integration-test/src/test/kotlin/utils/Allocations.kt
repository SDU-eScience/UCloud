package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.serviceClient

data class TotalWalletContent(
    val initialQuota: Long,
    val localUsage: Long,
    val treeUsage: Long
)

fun getSumOfWallets(wallets: List<WalletV2>): TotalWalletContent{
    val initialQuota = wallets.sumOf { wallet -> wallet.quota }
    val localUsage = wallets.sumOf { wallet -> wallet.localUsage }
    val treeUsage = wallets.sumOf { wallet -> wallet.allocationGroups.sumOf { it.group.usage } }
    return TotalWalletContent(initialQuota, localUsage, treeUsage)
}

suspend fun findWalletsInternal(walletOwner: WalletOwner): Set<WalletV2> {
    return AccountingV2.browseWalletsInternal.call(
        AccountingV2.BrowseWalletsInternal.Request(
            walletOwner
        ),
        serviceClient
    ).orThrow().wallets.toSet()
}

suspend fun findWallets(projectId: String, piClient: AuthenticatedClient): Set<WalletV2> {
    val result = HashSet<WalletV2>()

    var next: String? = null
    while (true) {
        val page = AccountingV2.browseWallets.call(
            AccountingV2.BrowseWallets.Request(
                itemsPerPage = 250,
                next = next,
            ),
            piClient.withProject(projectId)
        ).orThrow()

        page.items.forEach { result.add(it) }

        next = page.next ?: break
    }
    return result
}

/*suspend fun findAllocationsInternal(walletOwner: WalletOwner): Set<WalletAllocationB> {
    return findWalletsInternal(walletOwner).flatMap { it.allocations }.toSet()
}
suspend fun findAllocations(projectId: String, piClient: AuthenticatedClient): Set<WalletAllocationB> {
    return findWallets(projectId, piClient).flatMap { it.al }.toSet()
}*/
