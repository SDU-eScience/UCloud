package services.accounting

import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.services.accounting.*
import dk.sdu.cloud.accounting.services.accounting.allocationGroups
import dk.sdu.cloud.accounting.services.accounting.allocations
import dk.sdu.cloud.accounting.services.accounting.walletsById
import dk.sdu.cloud.accounting.services.accounting.walletsIdAccumulator
import kotlin.math.max
import kotlin.test.Test

class RandomTest {

    private suspend fun randomWalletCharge(n: Int, context: TestContext): Long {
        var total = 0L
        for (i in 0..n) {
            val walletId = (1 until walletsIdAccumulator.get()).random()
            total += randomCharge(walletId, 5, 10, context)
        }
        return total
    }

    private suspend fun randomRetire(numberOfAllocationsToRetire: Int) {
        for (i in 0..numberOfAllocationsToRetire) {
            val allocationId = (1..allocations.maxOf { it.key }).random()
            allocations[allocationId]!!.retired = true
        }
    }

    private suspend fun randomCharge(walletId: Int, maxAmount: Long, repetitions: Int, context: TestContext): Long {
        var total = 0L
        for (i in 0..repetitions) {
            val request = AccountingRequest.SystemCharge(
                amount = (1..maxAmount).random(),
                walletId = walletId.toLong()
            )
            total += request.amount
            val success = runCatching {
                context.accounting.sendRequest(request)
            }.isSuccess

            if (!success) return total
        }
        return total
    }

    private suspend fun createRandomHierarchy (maxHierarchyLevel: Int, numberOfWallets: Int, product: ProductCategoryIdV2, context: TestContext ) {
        if (maxHierarchyLevel < 1) return
        var walletsRemaining = numberOfWallets
        val projects = HashMap<Int,ArrayList<ProjectInfo>>()

        for (i in 1..maxHierarchyLevel) {
            projects[i] = ArrayList()

            val walletsOnLevel =
                if (i == maxHierarchyLevel) {
                    walletsRemaining
                } else {
                    (1..walletsRemaining).random()
                }

            for (j in 0 until walletsOnLevel) {
                val project = context.createProject()
                projects[i]!!.add(project)
                walletsRemaining--
            }
        }
        //create suballocations from root (level1)

        projects[1]?.forEach { projectInfo ->
            //1-3 allocations
            for (k in (1..(1..3).random())) {
                val quota = (1..20).random() * 100
                context.accounting.sendRequest(
                    AccountingRequest.SubAllocate(
                        context.provider.idCard,
                        product,
                        projectInfo.projectId,
                        quota.toLong(),
                        0,
                        10000
                    )
                )
            }
        }

        for (h in 2..maxHierarchyLevel) {
            val parentLevel = projects[h-1]
            val currentLevel = projects[h]

            if (currentLevel != null && currentLevel.size > 0) {
                //Give all children a single alloc
                currentLevel?.forEach { child ->
                    val randomParent = (0 until parentLevel!!.size).random()
                    val parent = parentLevel[randomParent]
                    val quota = (1..20).random() * 100
                    context.accounting.sendRequest(
                        AccountingRequest.SubAllocate(
                            parent.idCard,
                            product,
                            child.projectId,
                            quota.toLong(),
                            0,
                            10000
                        )
                    )
                }

                //Add random 1-5 additional allocation on level
                for (v in 1..5) {
                    val randomParent = (0 until parentLevel!!.size).random()
                    val parent = parentLevel[randomParent]

                    val randomChild = (0 until currentLevel!!.size).random()
                    val child = currentLevel[randomChild]

                    val quota = (1..20).random() * 100
                    context.accounting.sendRequest(
                        AccountingRequest.SubAllocate(
                            parent.idCard,
                            product,
                            child.projectId,
                            quota.toLong(),
                            0,
                            10000
                        )
                    )

                }
            }
        }

    }

    @Test
    fun randomTest() = withTest {
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        createRandomHierarchy(5, 20, product, this)

        println(walletsById)
        println(allocations)
        println(allocationGroups)

    }
}