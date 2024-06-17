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
    val treeUsage = wallets.sumOf { wallet -> wallet.totalUsage }
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
