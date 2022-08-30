package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.Launcher
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.test.assertThatInstance
import java.util.*
import kotlin.test.assertEquals

suspend fun retrieveWalletsInternal(walletOwner: WalletOwner, serviceClient: AuthenticatedClient): List<Wallet> {
    return Wallets.retrieveWalletsInternal.call(
        WalletsInternalRetrieveRequest(
            walletOwner
        ),
        serviceClient
    ).orThrow().wallets
}

suspend fun retrieveAllocationsInternal(
    owner: WalletOwner,
    categoryId: ProductCategoryId,
    serviceClient: AuthenticatedClient
): List<WalletAllocation> {
    return Wallets.retrieveAllocationsInternal.call(
        WalletAllocationsInternalRetrieveRequest(
            owner,
            categoryId
        ),
        serviceClient
    ).orThrow().allocations
}

suspend fun prepareProjectChain(
    rootBalance: Long,
    chainFromRoot: List<Allocation>,
    productCategory: ProductCategoryId,
    skipCreationOfLeaf: Boolean = false,
    breadth: Int = 1,
    moreProducts: List<ProductCategoryId> = emptyList(),
    serviceClient: AuthenticatedClient
): List<AllocationResult> {
    val irrelevantUser = createUser()
    val allProducts = listOf(productCategory) + moreProducts

    val leaves = ArrayList<AllocationResult>()
    for ((index, allocOwner) in chainFromRoot.withIndex()) {
        if (allocOwner.isProject) {
            val user = createUser()
            val project = Projects.create.call(
                CreateProjectRequest("P$index", principalInvestigator = user.username),
                UCloudLauncher.serviceClient
            ).orThrow()

            leaves.add(
                AllocationResult(
                    user.username,
                    project.id,
                    user.client.withProject(project.id),
                    allocOwner.amount * breadth
                )
            )
        } else {
            val user = createUser()
            leaves.add(AllocationResult(user.username, null, user.client, allocOwner.amount * breadth))
        }
    }

    for (category in allProducts) {
        repeat(breadth) {
            val rootPi = createUser()
            val rootProject = initializeRootProject(rootPi.username, amount = rootBalance)
            var previousAllocation: String =
                retrieveAllocationsInternal(WalletOwner.Project(rootProject), category, serviceClient).single().id
            var previousPi: AuthenticatedClient = rootPi.client
            val expectedAllocationPath = arrayListOf(previousAllocation)
            for ((index, leaf) in leaves.withIndex()) {
                if (index == leaves.lastIndex && skipCreationOfLeaf) break


                val request = bulkRequestOf(
                    DepositToWalletRequestItem(
                        leaf.owner,
                        previousAllocation,
                        leaf.balance / breadth,
                        "Deposit",
                        chainFromRoot[index].startDate,
                        chainFromRoot[index].endDate,
                        transactionId = UUID.randomUUID().toString()
                    )
                )
                Accounting.deposit.call(request, previousPi).orThrow()

                assertThatInstance(
                    Accounting.deposit.call(request, irrelevantUser.client),
                    "Should fail because they are not a part of the project"
                ) { it.statusCode.value in 400..499 }

                val alloc =
                    retrieveAllocationsInternal(leaf.owner, category, serviceClient).last()
                previousAllocation = alloc.id
                previousPi = leaf.client

                expectedAllocationPath.add(alloc.id)
                assertEquals(alloc.allocationPath, expectedAllocationPath)
                assertEquals(alloc.balance, leaf.balance / breadth)
                assertEquals(alloc.initialBalance, leaf.balance / breadth)
            }
        }
    }
    return leaves
}
