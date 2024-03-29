package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.serviceClient

suspend fun findWalletsInternal(walletOwner: WalletOwner): Set<Wallet> {
    return Wallets.retrieveWalletsInternal.call(
        WalletsInternalRetrieveRequest(
            walletOwner
        ),
        serviceClient
    ).orThrow().wallets.toSet()
}

suspend fun findWallets(projectId: String, piClient: AuthenticatedClient): Set<Wallet> {
    val result = HashSet<Wallet>()

    var next: String? = null
    while (true) {
        val page = Wallets.browse.call(
            WalletBrowseRequest(
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

suspend fun findAllocationsInternal(walletOwner: WalletOwner): Set<WalletAllocation> {
    return findWalletsInternal(walletOwner).flatMap { it.allocations }.toSet()
}
suspend fun findAllocations(projectId: String, piClient: AuthenticatedClient): Set<WalletAllocation> {
    return findWallets(projectId, piClient).flatMap { it.allocations }.toSet()
}
