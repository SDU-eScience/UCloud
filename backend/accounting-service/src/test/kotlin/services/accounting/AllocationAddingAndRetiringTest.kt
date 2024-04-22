package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.StaticTimeProvider
import kotlin.test.*
import kotlin.test.assertEquals
class AllocationAddingAndRetiringTest {



    @Test
    fun injectResourcesAfterRetirementNormalCharge() = withTest {
        injectResourcesAfterRetirement(false, this)
    }


    @Test
    fun injectResourcesAfterRetirementOverCharge() = withTest {
        injectResourcesAfterRetirement(true, this)
    }

    private suspend fun injectResourcesAfterRetirement(massiveOvercharge: Boolean, context: TestContext) = with(context) {
        val project = createProject()
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 2000000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 10, 0, 1000)
        )

        runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = if (massiveOvercharge) 500L else 10,
                    isDelta = true,
                )
            )
        }

        StaticTimeProvider.time = 2000L

        accounting.sendRequest(AccountingRequest.ScanRetirement(IdCard.System))

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 10, 0, 10000)
        )

        val maxUsable = accounting.sendRequest(
            AccountingRequest.MaxUsable(project.idCard, product)
        )

        if (massiveOvercharge) {
            assertEquals(0, maxUsable)
        } else {
            assertEquals(10, maxUsable)
        }

    }

    @Test
    fun injectResourcesAfterTotalUsageMassiveOvercharge() {
        withTest {
            injectResourcesAfterTotalUsage(true, false, this)
        }
    }

    @Test
    fun injectResourcesFromOtherRootAfterTotalUsageMassiveOvercharge() {
        withTest {
            injectResourcesAfterTotalUsage(true, true,  this)
        }
    }

    @Test
    fun injectResourcesAfterTotalUsageMaxCharge() {
        withTest {
            injectResourcesAfterTotalUsage(false, false, this)
        }
    }

    @Test
    fun injectResourcesFromOtherRootAfterTotalUsageMaxCharge() {
        withTest {
            injectResourcesAfterTotalUsage(false, true,  this)
        }
    }

    suspend fun injectResourcesAfterTotalUsage(massiveOvercharge: Boolean, secondAllocFromNewAllocator: Boolean, context: TestContext) = with(context) {
        val allocator = createProject()
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        //ALLOCATOR
        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, allocator.projectId, 1000, 0, 10000)
        )

        val secondAllocator = if (secondAllocFromNewAllocator) {
            val p = createProject()
            accounting.sendRequest(
                AccountingRequest.SubAllocate(provider.idCard, product, p.projectId, 1000, 0, 10000)
            )
            p
        } else null

        val project = createProject()
        accounting.sendRequest(
            AccountingRequest.SubAllocate(allocator.idCard, product, project.projectId, 10, 0, 10000)
        )


        runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = if (massiveOvercharge) 500L else 10,
                    isDelta = true,
                )
            )
        }

        if (secondAllocFromNewAllocator) {
            accounting.sendRequest(
                AccountingRequest.SubAllocate(secondAllocator!!.idCard, product, project.projectId, 10, 0, 10000)
            )
        } else {
            accounting.sendRequest(
                AccountingRequest.SubAllocate(allocator.idCard, product, project.projectId, 10, 0, 10000)
            )
        }

        val maxUsable = accounting.sendRequest(
            AccountingRequest.MaxUsable(project.idCard, product)
        )

        if (massiveOvercharge) {
            assertEquals(0, maxUsable)
        } else {
            assertEquals(10, maxUsable)
        }
    }

    @Test
    fun childUsesAllCheckParent() = withTest {
        val project = createProject()
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 10, 0, 10000)
        )

        val child = createProject()

        accounting.sendRequest(
            AccountingRequest.SubAllocate(project.idCard, product, child.projectId, 10, 0, 10000)
        )

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                child.projectId,
                product,
                amount = 10L,
                isDelta = true,
            )
        )

        val maxUsable = accounting.sendRequest(
            AccountingRequest.MaxUsable(project.idCard, product)
        )

        assertEquals(0, maxUsable)
    }
}