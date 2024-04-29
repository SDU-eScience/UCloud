package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.StaticTimeProvider
import kotlin.math.max
import kotlin.test.*

class NonDeltaChargeTest {

    @Test
    fun basicTest() = withTest {
        val project = createProject()
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 1000000L, 0, 10000)
        )

        var wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(1000L, wallet.maxUsable)
        assertEquals(1000000L, wallet.quota)
        assertEquals(0L, wallet.localUsage)
        assertEquals(0L, wallet.totalUsage)
        var success: Boolean

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 500L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(true, success)

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(500L, wallet.maxUsable)
        assertEquals(1000000L, wallet.quota)
        assertEquals(500L, wallet.localUsage)
        assertEquals(500L, wallet.totalUsage)

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 0,
                isDelta = false,
            )
        )
        assertEquals(true, success)

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(1000L, wallet.maxUsable)
        assertEquals(1000000L, wallet.quota)
        assertEquals(0L, wallet.localUsage)
        assertEquals(0L, wallet.totalUsage)


        StaticTimeProvider.time = 2000L
        accounting.sendRequest(AccountingRequest.ScanRetirement(IdCard.System))

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 300,
                    isDelta = false,
                )
            )
        }.isSuccess

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(false, success)
        assertEquals(0, wallet.maxUsable)
        assertEquals(1000000L, wallet.quota)
        assertEquals(300L, wallet.localUsage)
        assertEquals(300L, wallet.totalUsage)


        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 2000L, 5000L),
        )

        makeGraphFile(this, "nondelta")

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 0,
                    isDelta = false,
                )
            )
        }.isSuccess


        makeGraphFile(this, "lastcharge")

        wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(true, success)
        assertEquals(1000L, wallet.maxUsable)
        assertEquals(1000000L, wallet.quota)
        assertEquals(0L, wallet.localUsage)
        assertEquals(0L, wallet.totalUsage)

    }

    @Test
    fun `test over-spending and then gradual lowering`() = withTest {
        val project = createProject()
        val product = provider.capacityProduct
        val start = 0L
        val end = 1000L
        val quota = 10_000L

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, quota * 1000, start, end)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, quota, start, end)
        )

        suspend fun charge(amount: Long) {
            accounting.sendRequestNoUnwrap(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = amount,
                    isDelta = false,
                )
            )

            val maxUsable = accounting.sendRequest(
                AccountingRequest.MaxUsable(
                    project.idCard,
                    product,
                )
            )

            val expectedMaxUsable = max(0, quota - amount)
            assertEquals(expectedMaxUsable, maxUsable, "during charge of $amount")
        }

        // Sanity checking that we can go up and down as long as we stay below the quota
        charge(0)
        charge(quota / 3)
        charge(quota / 4)
        charge(quota / 2)
        charge(0)

        // Ensure that we handle being above the quota correctly
        charge(quota + 10_000)
        charge(quota + 5_000)
        charge(quota + 1)
        charge(quota + 0)
        charge(quota - 1)
        charge(quota + 1)
        charge(quota + 0)
        charge(quota - 5_000)
        charge(quota + 1)
        charge(quota - 5_000)
    }
}