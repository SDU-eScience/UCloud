package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.FakeIdCardService
import dk.sdu.cloud.accounting.util.FakeProductCache
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.service.NonDistributedLockFactory
import dk.sdu.cloud.service.NonDistributedStateFactory
import dk.sdu.cloud.service.StaticTimeProvider
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.test.*

class AccountingTest {
    private data class TestContext(
        val products: FakeProductCache,
        val idCards: FakeIdCardService,
        val accounting: AccountingSystem,
    ) {
        lateinit var provider: ProviderInfo
    }

    private fun withTest(fn: suspend TestContext.() -> Unit) {
        Time.provider = StaticTimeProvider
        val products = FakeProductCache()
        val idCards = FakeIdCardService(products)
        val accounting = AccountingSystem(
            products,
            FakeAccountingPersistence,
            idCards,
            NonDistributedLockFactory(),
            true,
            NonDistributedStateFactory(),
            "127.0.0.1",
        )

        runBlocking {
            val newScope = CoroutineScope(Dispatchers.IO + coroutineContext)
            val ctx = TestContext(products, idCards, accounting)
            ctx.provider = ctx.createProvider()

            ctx.accounting.start(newScope)
            ctx.fn()
            ctx.accounting.sendRequest(AccountingRequest.StopSystem(IdCard.System))
        }
    }

    private data class ProjectInfo(
        val projectId: String,
        val projectPi: String,
        val actorAndProject: ActorAndProject,
        val idCard: IdCard,
    )

    private suspend fun TestContext.createProject(): ProjectInfo {
        val projectId = UUID.randomUUID().toString()
        val project = idCards.createProject(projectId, canConsumeResources = false, parent = null)
        val projectPiUsername = "$projectId-PI"
        val projectPiUid = idCards.createUser(projectPiUsername)
        idCards.addAdminToProject(projectPiUid, project)
        val actorAndProject = ActorAndProject(Actor.SystemOnBehalfOfUser(projectPiUsername), projectId)
        val idCard = idCards.fetchIdCard(actorAndProject)
        return ProjectInfo(projectId, projectPiUsername, actorAndProject, idCard)
    }

    private data class ProviderInfo(
        val projectId: String,
        val providerId: String,
        val projectPi: String,
        val projectPiUid: Int,
        val actorAndProject: ActorAndProject,
        val idCard: IdCard,
        val providerCard: IdCard,
        val capacityProduct: ProductCategoryIdV2,
        val nonCapacityProduct: ProductCategoryIdV2,
    )

    private suspend fun TestContext.createProvider(): ProviderInfo {
        val providerId = UUID.randomUUID().toString()
        val project = idCards.createProject(providerId, canConsumeResources = false, parent = null)
        val projectPiUsername = "$providerId-PI"
        val projectPiUid = idCards.createUser(projectPiUsername)
        idCards.addAdminToProject(projectPiUid, project)
        idCards.markProviderProject(providerId, project)

        val allProducts = ArrayList<Int>()

        products.insert(
            ProductV2.Compute(
                NON_CAPACITY_PRODUCT,
                1L,
                ProductCategory(
                    NON_CAPACITY_PRODUCT,
                    providerId,
                    ProductType.COMPUTE,
                    AccountingUnit("Core", "Core", floatingPoint = false, displayFrequencySuffix = true),
                    AccountingFrequency.PERIODIC_HOUR
                ),
                cpu = 1,
                gpu = 0,
                memoryInGigs = 4,
                description = NON_CAPACITY_PRODUCT,
            )
        ).also { allProducts.add(it) }

        products.insert(
            ProductV2.Storage(
                CAPACITY_PRODUCT,
                1L,
                ProductCategory(
                    CAPACITY_PRODUCT,
                    providerId,
                    ProductType.STORAGE,
                    AccountingUnit("GB", "GB", floatingPoint = false, displayFrequencySuffix = false),
                    AccountingFrequency.ONCE,
                ),
                description = CAPACITY_PRODUCT,
            )
        ).also { allProducts.add(it) }

        val actorAndProject = ActorAndProject(Actor.SystemOnBehalfOfUser(projectPiUsername), providerId)
        val idCard = idCards.fetchIdCard(actorAndProject)

        return ProviderInfo(
            providerId,
            providerId,
            projectPiUsername,
            projectPiUid,
            actorAndProject,
            idCard,
            IdCard.Provider(providerId, allProducts.toIntArray()),
            ProductCategoryIdV2(CAPACITY_PRODUCT, providerId),
            ProductCategoryIdV2(NON_CAPACITY_PRODUCT, providerId),
        )
    }

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

    companion object {
        private const val CAPACITY_PRODUCT = "capacity"
        private const val NON_CAPACITY_PRODUCT = "nonCapacity"
        private const val TIMESTAMP_OFFSET = 1710000000000000L
    }
}