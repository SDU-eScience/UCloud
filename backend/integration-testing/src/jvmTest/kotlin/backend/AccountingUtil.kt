package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow

suspend fun retrieveWalletsInternal(walletOwner: WalletOwner, client: AuthenticatedClient): List<Wallet> {
    return Wallets.retrieveWalletsInternal.call(
        WalletsInternalRetrieveRequest(
            walletOwner
        ),
        client
    ).orThrow().wallets
}

suspend fun retrieveAllocationsInternal(
    owner: WalletOwner,
    categoryId: ProductCategoryId,
    client: AuthenticatedClient
): List<WalletAllocation> {
    return Wallets.retrieveAllocationsInternal.call(
        WalletAllocationsInternalRetrieveRequest(
            owner,
            categoryId
        ),
        client
    ).orThrow().allocations
}
