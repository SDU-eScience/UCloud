package services.accounting

import dk.sdu.cloud.accounting.api.AccountingV2
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.services.accounting.*
import dk.sdu.cloud.accounting.services.accounting.allocationGroups
import dk.sdu.cloud.accounting.services.accounting.allocations
import dk.sdu.cloud.accounting.services.accounting.walletsById
import dk.sdu.cloud.accounting.services.accounting.walletsIdAccumulator
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.Time
import org.joda.time.DateTime
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test

class RandomTest {

    private suspend fun randomRetire(numberOfAllocationsToRetire: Int) {
        for (i in 0..numberOfAllocationsToRetire) {
            val allocationId = (1..allocations.maxOf { it.key }).random()
            allocations[allocationId]!!.retired = true
        }
    }

    private suspend fun randomCharge(walletId: Int, maxAmount: Long, repetitions: Int, context: TestContext): Long {
        println("Charging $repetitions times from $walletId")
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
        with(context) {
            if (maxHierarchyLevel < 1) return
            var walletsRemaining = numberOfWallets
            val projects = HashMap<Int, ArrayList<ProjectInfo>>()

            for (i in 1..maxHierarchyLevel) {
                projects[i] = ArrayList()

                val walletsOnLevel =
                    if (i == maxHierarchyLevel || walletsRemaining == 0) {
                        walletsRemaining
                    } else {
                        min((1..walletsRemaining).random(), (numberOfWallets / maxHierarchyLevel + i).toInt())
                    }

                for (j in 0 until walletsOnLevel) {
                    val project = createProject()
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
                val parentLevel = projects[h - 1]
                val currentLevel = projects[h]

                if (currentLevel != null && currentLevel.size > 0) {
                    //Give all children a single alloc
                    currentLevel.forEach { child ->
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
                    val numberOfadditionalAllocationsOnLevel = (1..5).random()
                    for (v in 1..numberOfadditionalAllocationsOnLevel) {
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
    }

    private suspend fun makeGraphFile(numberOfWallets: Int, context: TestContext) {
        with(context) {
            val allNodes = (1..numberOfWallets+1).toList()
            accounting.sendRequest(AccountingRequest.DebugState(IdCard.System, allNodes)).also {
                val time = DateTime.now()
                File("/tmp/hierarchy-${time}.txt").writeText(buildString {
                    appendLine("```mermaid")
                    appendLine(it)
                    appendLine("```")
                })
            }
        }
    }

    private suspend fun randomTest(
        product: ProductCategoryIdV2,
        numberOfWallets: Int,
        maxHierarchyLevel: Int,
        numberOfCharges: Int,
        context: TestContext,
        singleWalletToCharge: Boolean = true,
        rootQuota: Long = 1000L,
        specificChargeAmount: Long? = null,
    ) {
        with(context) {
            accounting.sendRequest(
                AccountingRequest.RootAllocate(provider.idCard, product, rootQuota, 0, 1000)
            )

            createRandomHierarchy(maxHierarchyLevel, numberOfWallets, product, context)

            var charged = 0L
            if (singleWalletToCharge) {
                val walletToCharge = (2..numberOfWallets).random()
                for (k in 1..numberOfCharges)  {
                    if (specificChargeAmount != null) {
                        charged += specificChargeAmount
                        val success = runCatching {
                                accounting.sendRequest(
                                    AccountingRequest.SystemCharge(
                                        amount = specificChargeAmount,
                                        walletId = walletToCharge.toLong()
                                    )
                                )
                            }.isSuccess
                        if (!success) break

                    } else {
                        charged += randomCharge(
                            walletId = walletToCharge,
                            maxAmount = 100,
                            repetitions = (1..3).random(),
                            context = this
                        )
                    }
                }
            } else {
                for (k in 1..numberOfCharges) {
                    val walletToCharge = (2..numberOfWallets).random().toLong()
                    if (specificChargeAmount != null) {
                        charged += specificChargeAmount
                        runCatching {
                            accounting.sendRequest(
                                AccountingRequest.SystemCharge(
                                    amount = specificChargeAmount,
                                    walletId = walletToCharge
                                )
                            )
                        }
                    } else {
                        charged += randomCharge(
                            walletId = walletToCharge.toInt(),
                            maxAmount = 100,
                            repetitions = (1..3).random(),
                            context = this
                        )
                    }
                }
            }

            for (i in 1..numberOfWallets) {
                try {
                    checkWalletHierarchy(i)
                } catch (ex: Throwable) {
                    val allNodes = (1..numberOfWallets+1).toList()

                    accounting.sendRequest(AccountingRequest.DebugState(IdCard.System, allNodes)).also {
                        File("/tmp/hierarchyWithErrorWalletId${i}.txt").writeText(buildString {
                            appendLine("```mermaid")
                            appendLine(it)
                            appendLine("```")
                        })
                    }
                }
            }

            if (specificChargeAmount != null) {
                assert(specificChargeAmount == charged)
            }
        }
    }

    /*
    NonCapacity Product Testing
     */
    @Test
    fun singleRandomChargeNonCapacityLowHierarchyTree() = withTest {
        randomTest(provider.nonCapacityProduct, 2, 2, 1, context = this)
    }

    @Test
    fun singleRandomChargeNonCapacityLowHierarchyTreeNoQuotaInRoot() = withTest {
        randomTest(provider.nonCapacityProduct, 2, 2, 1, rootQuota = 0, context = this)
    }

    @Test
    fun singleRandomChargeNonCapacityLowHierarchyTreeManyWallets() = withTest {
        randomTest(provider.nonCapacityProduct, 200, 2, 1, context = this)
    }

    @Test
    fun singleRandomChargeNonCapacityHighHierarchyTreeManyWallets() = withTest {
        randomTest(provider.nonCapacityProduct, 200, 15, 1, context = this)
    }

    @Test
    fun singleSpecificChargeNonCapacityLowHierarchyTreeHighCharge() = withTest {
        randomTest(provider.nonCapacityProduct, 2, 2, 1, rootQuota = 1000, specificChargeAmount = 950, context = this)
    }

    @Test
    fun singleSpecificChargeNonCapacityLowHierarchyTreeOverCharge() = withTest {
        randomTest(provider.nonCapacityProduct, 2, 2, 1, rootQuota = 1000, specificChargeAmount = 10000, context = this)
    }

    @Test
    fun multipleRandomChargesNonCapacityLowHierarchyTree() = withTest {
        randomTest(provider.nonCapacityProduct, 2, 2, 100, context = this)
    }

    /*
    Capacity Product Testing
     */

    @Test
    fun singleRandomChargeCapacityLowHierarchyTree() = withTest {
        randomTest(provider.capacityProduct, 2, 2, 1, context = this)
    }

    @Test
    fun multipleRandomChargesCapacityLowHierarchyTree() = withTest {
        randomTest(provider.capacityProduct, 2, 2, 100, context = this)
    }
}