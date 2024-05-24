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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.collections.ArrayList

data class TestContext(
    val products: FakeProductCache,
    val idCards: FakeIdCardService,
    val accounting: AccountingSystem,
) {
    lateinit var provider: ProviderInfo
}

fun withTest(fn: suspend TestContext.() -> Unit) {
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

data class ProjectInfo(
    val projectId: String,
    val projectPi: String,
    val actorAndProject: ActorAndProject,
    val idCard: IdCard,
)

suspend fun TestContext.createProject(): ProjectInfo {
    val projectId = UUID.randomUUID().toString()
    val projectPiUsername = "$projectId-PI"
    val projectPiUid = idCards.createUser(projectPiUsername)
    val project = idCards.createProject(projectId, pi = projectPiUsername, canConsumeResources = false, parent = null)
    idCards.addAdminToProject(projectPiUid, project)
    val actorAndProject = ActorAndProject(Actor.SystemOnBehalfOfUser(projectPiUsername), projectId)
    val idCard = idCards.fetchIdCard(actorAndProject)
    return ProjectInfo(projectId, projectPiUsername, actorAndProject, idCard)
}

data class ProviderInfo(
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

suspend fun TestContext.createProvider(): ProviderInfo {
    val providerId = UUID.randomUUID().toString()
    val projectPiUsername = "$providerId-PI"
    val projectPiUid = idCards.createUser(projectPiUsername)
    val project = idCards.createProject(providerId, pi = projectPiUsername, canConsumeResources = false, parent = null)
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
const val CAPACITY_PRODUCT = "capacity"
const val NON_CAPACITY_PRODUCT = "nonCapacity"
const val TIMESTAMP_OFFSET = 1710000000000000L