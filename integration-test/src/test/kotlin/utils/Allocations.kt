package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletAllocation
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject

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

suspend fun findAllocations(projectId: String, piClient: AuthenticatedClient): Set<WalletAllocation> {
    return findWallets(projectId, piClient).flatMap { it.allocations }.toSet()
}
