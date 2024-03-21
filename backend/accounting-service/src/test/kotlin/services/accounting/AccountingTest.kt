package dk.sdu.cloud.accounting.services.accounting


import dk.sdu.cloud.accounting.api.*

import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.StaticTimeProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import services.accounting.*
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.test.*

class AccountingTest {

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

    private suspend fun TestContext.runSimulation(maxTimeToRun: Long, capacityBased: Boolean) {
        val product = provider.nonCapacityProduct

        accounting.sendRequest(
            AccountingRequest.RootAllocate(
                provider.idCard,
                product,
                1000L,
                TIMESTAMP_OFFSET + 0,
                TIMESTAMP_OFFSET + 100000
            )
        )

        val timeSync = MutableSharedFlow<Boolean>()

        coroutineScope {
            val deadline = System.currentTimeMillis() + maxTimeToRun
            StaticTimeProvider.time = TIMESTAMP_OFFSET
            val actors = ArrayList<TestActor>()

            repeat(Random.nextInt(50) + 10) {
                val actor = TestActor(createProject())
                actors.add(actor)
                accounting.sendRequest(
                    AccountingRequest.SubAllocate(
                        provider.idCard,
                        product,
                        actor.project.projectId,
                        Random.nextLong(1, 1000),
                        TIMESTAMP_OFFSET,
                        TIMESTAMP_OFFSET + Random.nextLong(10000),
                    )
                )
            }

            while (System.currentTimeMillis() < deadline) {
                StaticTimeProvider.time++
                timeSync.emit(true)
            }
        }
    }

    private data class TestActor(
        val project: ProjectInfo,
        val depth: Int = 1,
        var hasResources: Boolean = true,
    )

    private data class ActorOutput(
        val newActors: List<TestActor>,
        val tasksPerformed: Int,
    )

    private suspend fun TestContext.runActor(actor: TestActor) {

    }

    @Test
    fun scopeTest() = withTest {
        val project = createProject()
        val product = provider.nonCapacityProduct
        val jobA = "jobA"
        val jobB = "jobB"

        accounting.sendRequest(
            AccountingRequest.RootAllocate(provider.idCard, product, 1000L, 0, 1000)
        )

        accounting.sendRequest(
            AccountingRequest.SubAllocate(provider.idCard, product, project.projectId, 1000000L, 0, 10000)
        )

        suspend fun checkUsage(expected: Long) {
            val wallet = accounting.sendRequest(AccountingRequest.BrowseWallets(project.idCard)).single()
            assertEquals(expected, wallet.localUsage)
        }

        suspend fun chargeJob(jobId: String, priorUsage: Long) {
            repeat(5) { count ->
                accounting.sendRequest(
                    AccountingRequest.Charge(
                        provider.providerCard,
                        project.projectId,
                        product,
                        amount = 100L,
                        isDelta = true,
                        scope = jobId
                    )
                )
                checkUsage((count + 1) * 100L + priorUsage)
            }
        }

        chargeJob(jobA, 0L)
        chargeJob(jobB, 500L)

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 300L,
                isDelta = false,
                scope = jobB
            )
        )

        checkUsage(800)

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 50L,
                isDelta = false,
                scope = jobB
            )
        )

        checkUsage(550)

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 100L,
                isDelta = true,
                scope = null
            )
        )

        checkUsage(650)

        accounting.sendRequest(
            AccountingRequest.Charge(
                provider.providerCard,
                project.projectId,
                product,
                amount = 900L,
                isDelta = false,
                scope = null
            )
        )

        checkUsage(900)
    }

}