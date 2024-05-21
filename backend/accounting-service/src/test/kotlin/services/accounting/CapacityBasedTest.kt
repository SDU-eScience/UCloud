package services.accounting

import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.createProject
import dk.sdu.cloud.accounting.services.accounting.withTest
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.StaticTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class CapacityBasedTest {

    @Test
    fun capacityParentRetireAfterChildOverspend() = withTest{
        val project = createProject()
        val product = provider.capacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 500L, 0, 10000)
        )

        var success: Boolean = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 100L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(true, success)

        var rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(900L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(100L, rootWallet.totalUsage)

        var projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(400L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(100L, projectWallet.localUsage)
        assertEquals(100L, projectWallet.totalUsage)

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 600L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(false, success)

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(500L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(500L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(0L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(600L, projectWallet.localUsage)
        assertEquals(600L, projectWallet.totalUsage)

        StaticTimeProvider.time = 2000L
        accounting.sendRequest(AccountingRequest.ScanRetirement(IdCard.System))

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(0L, rootWallet.maxUsable)
        assertEquals(0L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(500L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(0L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(600L, projectWallet.localUsage)
        assertEquals(600L, projectWallet.totalUsage)

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 200L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(false, success)

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(0L, rootWallet.maxUsable)
        assertEquals(0L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(200L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(0L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(200L, projectWallet.localUsage)
        assertEquals(200L, projectWallet.totalUsage)

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 2000, 10000)
        )

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(800L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(200L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(300L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(200L, projectWallet.localUsage)
        assertEquals(200L, projectWallet.totalUsage)

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 300L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(true, success)

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(700L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(300L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(200L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(300L, projectWallet.localUsage)
        assertEquals(300L, projectWallet.totalUsage)

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

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(500L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(500L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(0L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(500L, projectWallet.localUsage)
        assertEquals(500L, projectWallet.totalUsage)

        success = runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 600L,
                    isDelta = false,
                )
            )
        }.isSuccess

        assertEquals(false, success)

        rootWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(provider.idCard)).first()

        assertEquals(500L, rootWallet.maxUsable)
        assertEquals(1000L, rootWallet.quota)
        assertEquals(0L, rootWallet.localUsage)
        assertEquals(500L, rootWallet.totalUsage)

        projectWallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).first()

        assertEquals(0L, projectWallet.maxUsable)
        assertEquals(500L, projectWallet.quota)
        assertEquals(600L, projectWallet.localUsage)
        assertEquals(600L, projectWallet.totalUsage)
    }
}