package services.accounting

import dk.sdu.cloud.accounting.services.accounting.*
import kotlin.test.Test
import kotlin.test.assertEquals

class MixedChargeMethodTest {

    @Test
    fun mixedChargeWithinLimitsCapacity() = withTest {
        mixedCharge(isCapacityBased = true, withinLimit = true)
    }
    @Test
    fun mixedChargeOverLimitsCapacity() = withTest {
        mixedCharge(isCapacityBased = true, withinLimit = false)
    }

    @Test
    fun mixedChargeWithinLimitsNonCapacity() = withTest {
        mixedCharge(isCapacityBased = false, withinLimit = true)
    }
    @Test
    fun mixedChargeOverLimitsNonCapacity() = withTest {
        mixedCharge(isCapacityBased = false, withinLimit = false)
    }

    private fun mixedCharge(isCapacityBased: Boolean, withinLimit: Boolean) = withTest {
        val project = createProject()
        val product = if (isCapacityBased) provider.capacityProduct else provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 100, 0, 10000)
        )

        //delta charge
        var success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = if (withinLimit) 50L else 500L,
                    isDelta = true,
                )
            )
        }.isSuccess

        assertEquals(withinLimit, success)

        var wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(if (withinLimit) 50L else 0L, wallet.maxUsable)
        assertEquals(100L, wallet.quota)
        assertEquals(if (withinLimit) 50L else 500L, wallet.localUsage)
        assertEquals(if (withinLimit) 50L else 500L, wallet.totalUsage)

        //nondeltacharge to lower

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 20L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(true, success)

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(80L, wallet.maxUsable)
        assertEquals(100L, wallet.quota)
        assertEquals(20L, wallet.localUsage)
        assertEquals(20L, wallet.totalUsage)

        //delta charge again

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 30L,
                    isDelta = true,
                )
            )
        }.isSuccess

        assertEquals(true, success)

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(50L, wallet.maxUsable)
        assertEquals(100L, wallet.quota)
        assertEquals(50L, wallet.localUsage)
        assertEquals(50L, wallet.totalUsage)
    }
}