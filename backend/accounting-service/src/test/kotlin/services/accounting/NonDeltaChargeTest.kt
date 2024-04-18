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

//        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 500L,
                isDelta = false,
            )
        )

//        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 0,
                isDelta = false,
            )
        )

//        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))

        StaticTimeProvider.time = 2000L
        accounting.sendRequest(AccountingRequest.ScanRetirement(IdCard.System))
//        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))
        runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 300,
                    isDelta = false,
                )
            )
        }
//        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 2000L, 5000L),
        )
        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))
        runCatching {
            accounting.sendRequest(
                AccountingRequest.Charge(
                    provider.providerCard,
                    project.projectId,
                    product,
                    amount = 0,
                    isDelta = false,
                )
            )
        }
        println(accounting.sendRequest(AccountingRequest.MaxUsable(project.idCard, product)))

        println("Goodbye")
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