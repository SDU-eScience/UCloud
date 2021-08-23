package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import io.ktor.http.*
import java.util.*
import kotlin.math.max
import kotlin.test.assertEquals

suspend fun findWallet(
    client: AuthenticatedClient,
    product: ProductCategoryId
): Wallet? {
    return Wallets.browse.call(
        WalletBrowseRequest(),
        client
    ).orThrow().items.find { it.paysFor == product }
}

suspend fun findPersonalWallet(
    username: String,
    client: AuthenticatedClient,
    product: ProductCategoryId
): Wallet? {
    return Wallets.browse.call(
        WalletBrowseRequest(),
        client
    ).orThrow().items.find { it.paysFor == product }
}

suspend fun findProjectWallet(
    projectId: String,
    client: AuthenticatedClient,
    product: ProductCategoryId
): Wallet? {
    return Wallets.browse.call(
        WalletBrowseRequest(),
        client.withProject(projectId)
    ).orThrow().items.find { it.paysFor == product }
}

class Allocation(
    val isProject: Boolean,
    val amount: Long,
    val startDate: Long? = null,
    val endDate: Long? = null
)

data class AllocationResult(
    val username: String,
    val projectId: String?,
    val client: AuthenticatedClient,
    val balance: Long
) {
    val owner: WalletOwner = if (projectId == null) {
        WalletOwner.User(username)
    } else {
        WalletOwner.Project(projectId)
    }
}

suspend fun prepareProjectChain(
    rootBalance: Long,
    chainFromRoot: List<Allocation>,
    productCategory: ProductCategoryId,
    skipCreationOfLeaf: Boolean = false,
    breadth: Int = 1,
    moreProducts: List<ProductCategoryId> = emptyList(),
): List<AllocationResult> {
    val irrelevantUser = createUser()
    val allProducts = listOf(productCategory) + moreProducts

    val leaves = ArrayList<AllocationResult>()

    for ((index, allocOwner) in chainFromRoot.withIndex()) {
        if (allocOwner.isProject) {
            val user = createUser()
            val project = Projects.create.call(
                CreateProjectRequest("P$index", principalInvestigator = user.username),
                serviceClient
            ).orThrow()

            leaves.add(
                AllocationResult(
                    user.username,
                    project.id,
                    user.client.withProject(project.id),
                    allocOwner.amount * breadth
                )
            )
        } else {
            val user = createUser()
            leaves.add(AllocationResult(user.username, null, user.client, allocOwner.amount * breadth))
        }
    }

    for (category in allProducts) {
        repeat(breadth) {
            val rootPi = createUser()
            val rootProject = initializeRootProject(rootPi.username, amount = rootBalance)
            var previousAllocation: String =
                findProjectWallet(rootProject, rootPi.client, category)!!.allocations.single().id
            var previousPi: AuthenticatedClient = rootPi.client
            val expectedAllocationPath = arrayListOf(previousAllocation)
            for ((index, leaf) in leaves.withIndex()) {
                if (index == leaves.lastIndex && skipCreationOfLeaf) break

                val request = bulkRequestOf(
                    DepositToWalletRequestItem(
                        leaf.owner,
                        previousAllocation,
                        leaf.balance / breadth,
                        "Transfer",
                        chainFromRoot[index].startDate,
                        chainFromRoot[index].endDate
                    )
                )
                Accounting.deposit.call(request, previousPi).orThrow()

                assertThatInstance(
                    Accounting.deposit.call(request, irrelevantUser.client),
                    "Should fail because they are not a part of the project"
                ) { it.statusCode.value in 400..499 }

                val alloc =
                    findWallet(leaf.client, category)!!.allocations.last()
                previousAllocation = alloc.id
                previousPi = leaf.client

                expectedAllocationPath.add(alloc.id)
                assertEquals(alloc.allocationPath, expectedAllocationPath)
                assertEquals(alloc.balance, leaf.balance / breadth)
                assertEquals(alloc.initialBalance, leaf.balance / breadth)
            }
        }
    }
    return leaves
}

class AccountingTest : IntegrationTest() {
    override fun defineTests() {
        testFilter = { title, subtitle ->
            title == "Differential quota products" //&& subtitle == "Multiple allocation, with over-charge in leaf"
        }

        run {
            class In(
                val sourceIsProject: Boolean,
                val targetIsProject: Boolean,
                val numberOfTransfers: Int = 1,
                val transferAmount: Long = 500.DKK,
                val includeRootProjectWallet: Boolean = false,
                val expectFailure: Boolean = false,
                val rootWalletAllocationAmount: Long = 10000.DKK,
                val alternativeTargetWalletOwner: WalletOwner? = null,
                val alternativeTransferProduct: ProductCategoryId? = null,
                val transferToSelf: Boolean = false,
                val noRequest: Boolean = false
            )

            class Out(
                val sourceWallets: PageV2<Wallet>,
                val targetWallets: PageV2<Wallet>,
                val rootWallets: PageV2<Wallet>?
            )

            test<In, Out>("Transfers") {
                execute {
                    //Create products
                    createSampleProducts()
                    //Create Users
                    val user1 = createUser("user1")
                    val user2 = createUser("user2")
                    val user3 = createUser("user3")
                    val users = listOf(user1, user2, user3)

                    //Create project chain of 3 to ucloud root project and change PIs to user1, user2, user3
                    val leaves = prepareProjectChain(
                        input.rootWalletAllocationAmount,
                        (0 until 3).map { Allocation(true, 1000.DKK) },
                        sampleCompute.category
                    )

                    val projectIds = leaves.map { it.projectId }

                    projectIds.forEachIndexed { index, projectId ->
                        Projects.invite.call(
                            InviteRequest(projectId!!, setOf("user${index+1}")),
                            leaves[index].client
                        ).orThrow()

                        Projects.acceptInvite.call(
                            AcceptInviteRequest(projectId),
                            users[index].client
                        ).orThrow()

                        Projects.transferPiRole.call(
                            TransferPiRoleRequest("user${index+1}"),
                            leaves[index].client
                        ).orThrow()
                    }

                    //Create target and add storage of 10000 DKK
                    val targetId = if (input.targetIsProject) {
                        val projectName = "rootProject"
                        Projects.create.call(
                            CreateProjectRequest(projectName, principalInvestigator = user1.username),
                            serviceClient
                        ).orThrow().id
                    } else user1.username

                    val targetOwner =
                        if (input.targetIsProject) WalletOwner.Project(targetId) else WalletOwner.User(targetId)

                    //deposit to either user or project
                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleStorage.category,
                                targetOwner,
                                10000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    suspend fun transfer(
                        source: WalletOwner,
                        target: WalletOwner,
                        amount: Long,
                        performedBy: CreatedUser,
                        expectFailure: Boolean,
                        categoryId: ProductCategoryId?,
                        alternativeTarget: WalletOwner?,
                        transferToSelf: Boolean,
                        noRequest: Boolean
                    )
                    {
                        val chosenTarget = if ( transferToSelf ) {
                            source
                        } else {
                            alternativeTarget ?: target
                        }
                        try {
                            Accounting.transfer.call(
                                if (noRequest) {
                                    BulkRequest<TransferToWalletRequestItem>(emptyList())
                                } else {
                                    bulkRequestOf(
                                        TransferToWalletRequestItem(
                                            categoryId ?: sampleCompute.category,
                                            chosenTarget,
                                            source,
                                            amount,
                                        )
                                    )
                                },
                                performedBy.client
                            ).orThrow()
                        } catch (ex: RPCException) {
                            if (!expectFailure) {
                                throw ex
                            }
                        }
                    }

                    when {
                        input.sourceIsProject -> {
                            for (i in 1..input.numberOfTransfers) {
                                transfer(
                                    WalletOwner.Project(projectIds.last()!!),
                                    targetOwner,
                                    input.transferAmount,
                                    performedBy = user3,
                                    expectFailure = input.expectFailure,
                                    alternativeTarget = input.alternativeTargetWalletOwner,
                                    categoryId = input.alternativeTransferProduct,
                                    transferToSelf = input.transferToSelf,
                                    noRequest = input.noRequest
                                )
                            }
                        }
                        //source is user
                        !input.sourceIsProject -> {
                            val sourceOwner = WalletOwner.User(user2.username)
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        sourceOwner,
                                        1000.DKK,
                                        "Initial deposit"
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                            for (i in 1..input.numberOfTransfers) {
                                transfer(
                                    sourceOwner,
                                    targetOwner,
                                    input.transferAmount,
                                    performedBy = user2,
                                    expectFailure = input.expectFailure,
                                    alternativeTarget = input.alternativeTargetWalletOwner,
                                    categoryId = input.alternativeTransferProduct,
                                    transferToSelf = input.transferToSelf,
                                    noRequest = input.noRequest
                                )
                            }
                        }
                    }
                    val targetWallets = Wallets.browse.call(
                        WalletBrowseRequest(),
                        if ( input.targetIsProject) user1.client.withProject(targetId) else user1.client
                    ).orThrow()
                    val sourceWallets = Wallets.browse.call(
                        WalletBrowseRequest(),
                        if ( input.sourceIsProject) user3.client.withProject(projectIds.last()!!) else user2.client
                    ).orThrow()

                    val rootWallet = if (input.includeRootProjectWallet) {
                        Wallets.browse.call(
                            WalletBrowseRequest(),
                            user1.client.withProject(projectIds.first()!!)
                        ).orThrow()
                    } else null

                    Out(sourceWallets = sourceWallets, targetWallets = targetWallets, rootWallets = rootWallet)
                }

                case("project to project - single transfer") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(500.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(500.DKK, target?.allocations?.single()?.balance)
                    }
                }

                case("project to user - single transfer") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = false
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(500.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(500.DKK, target?.allocations?.single()?.balance)
                    }
                }

                case("user to project - single transfer") {
                    input(
                        In(
                            sourceIsProject= false,
                            targetIsProject = true
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(500.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(500.DKK, target?.allocations?.single()?.balance)
                    }
                }

                case("user to user - single transfer") {
                    input(
                        In(
                            sourceIsProject = false,
                            targetIsProject = false
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(500.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(500.DKK, target?.allocations?.single()?.balance)
                    }
                }

                case("Transfer over limit") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount =  100000.DKK
                        )
                    )
                    expectStatusCode(HttpStatusCode.PaymentRequired)
                }

                case("user to user - multiple transfers") {
                    input(
                        In(
                            sourceIsProject = false,
                            targetIsProject = false,
                            transferAmount = 10.DKK,
                            numberOfTransfers = 3
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(970.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(input.numberOfTransfers, target?.allocations?.size)
                        for (i in 0 until input.numberOfTransfers) {
                            assertEquals(input.transferAmount, target?.allocations?.get(i)?.balance)
                        }
                    }
                }

                case("user to project - multiple transfers") {
                    input(
                        In(
                            sourceIsProject = false,
                            targetIsProject = true,
                            transferAmount = 20.DKK,
                            numberOfTransfers = 4
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(920.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(input.numberOfTransfers, target?.allocations?.size)
                        for (i in 0 until input.numberOfTransfers) {
                            assertEquals(input.transferAmount, target?.allocations?.get(i)?.balance)
                        }
                    }
                }

                case("project to user - multiple transfers") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = false,
                            transferAmount = 10.DKK,
                            numberOfTransfers = 4,
                            includeRootProjectWallet = true
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(960.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(input.numberOfTransfers, target?.allocations?.size)
                        for (i in 0 until input.numberOfTransfers) {
                            assertEquals(input.transferAmount, target?.allocations?.get(i)?.balance)
                        }
                        assertEquals(960.DKK, output.rootWallets?.items?.find { it.paysFor == sampleCompute.category }?.allocations?.single()?.balance)
                    }
                }

                case("project to project - multiple transfers") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 10.DKK,
                            numberOfTransfers = 10,
                            includeRootProjectWallet = true
                        )
                    )
                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(900.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(input.numberOfTransfers, target?.allocations?.size)
                        for (i in 0 until input.numberOfTransfers) {
                            assertEquals(input.transferAmount, target?.allocations?.get(i)?.balance)
                        }
                        assertEquals(900.DKK, output.rootWallets?.items?.find { it.paysFor == sampleCompute.category }?.allocations?.single()?.balance)
                    }
                }

                case("project to project - multiple transfers - last fails") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 100.DKK,
                            numberOfTransfers = 11,
                            includeRootProjectWallet = true,
                            expectFailure = true
                        )
                    )

                    check {
                        val targetWallets = output.targetWallets.items
                        val sourceWallets = output.sourceWallets.items
                        val target = targetWallets.find { it.paysFor.name == sampleCompute.category.name }
                        val supposedCompletedTransfers = input.numberOfTransfers -1
                        assertEquals(1000.DKK, sourceWallets.single().allocations.single().initialBalance)
                        assertEquals(0.DKK, sourceWallets.single().allocations.single().balance)
                        assertEquals(supposedCompletedTransfers , target?.allocations?.size)
                        for (i in 0 until supposedCompletedTransfers) {
                            assertEquals(input.transferAmount, target?.allocations?.get(i)?.balance)
                        }
                        assertEquals(0.DKK, output.rootWallets?.items?.first()?.allocations?.first()?.balance)
                    }
                }

                case("project to project - root wallet does not have enough credits but allocations has") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 500.DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                            expectFailure = false,
                            rootWalletAllocationAmount = 100.DKK
                        )
                    )
                    expectStatusCode(HttpStatusCode.PaymentRequired)
                }

                case("Transfer with negative units") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = (-100).DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("Transfer to non existing wallet") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 100.DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                            alternativeTargetWalletOwner = WalletOwner.Project("Not A project")
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("Source missing product") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 100.DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                            alternativeTransferProduct = sampleIngress.category
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("transfer to self") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 100.DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                            transferToSelf = true
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("no request") {
                    input(
                        In(
                            sourceIsProject = true,
                            targetIsProject = true,
                            transferAmount = 100.DKK,
                            numberOfTransfers = 1,
                            includeRootProjectWallet = true,
                            noRequest = true
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

            }
        }

        run {
            class In(
                val owner: WalletOwner,
                val createProducts: Boolean = true,
                val createUser: String? = null
            )

            test<In, Unit>("Bad uses of rootDeposit") {
                execute {
                    if (input.createProducts) {
                        createSampleProducts()
                    }

                    val username = input.createUser
                    if (username != null) createUser(username)

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(sampleCompute.category, input.owner, 100.DKK, "Initial balance")
                        ),
                        serviceClient
                    ).orThrow()
                }

                case("Bad user") {
                    input(In(WalletOwner.User("BADUSER"), createProducts = true))
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("Bad project") {
                    input(In(WalletOwner.Project("BADPROJECT"), createProducts = true))
                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("Bad product") {
                    val id = UUID.randomUUID().toString()
                    input(In(WalletOwner.User(id), createProducts = false, createUser = id))
                    expectStatusCode(HttpStatusCode.BadRequest)
                }
            }
        }

        run {
            class In(
                val walletBelongsToProject: Boolean,
                val initialBalance: Long,
                val units: Long,
                val numberOfProducts: Long = 1,
                val product: Product = sampleCompute
            )

            class Out(
                val newBalance: Long
            )

            test<In, Out>("Root deposit and charge") {
                execute {
                    createSampleProducts()

                    val owner: WalletOwner
                    val client: AuthenticatedClient
                    val createdUser = createUser("${title}_$testId")

                    if (input.walletBelongsToProject) {
                        val id = Projects.create.call(
                            CreateProjectRequest(
                                "${title}_$testId",
                                null,
                                principalInvestigator = createdUser.username
                            ),
                            serviceClient
                        ).orThrow().id

                        owner = WalletOwner.Project(id)
                        client = createdUser.client.withProject(id)
                    } else {
                        client = createdUser.client
                        owner = WalletOwner.User(createdUser.username)
                    }

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                input.product.category,
                                owner,
                                input.initialBalance,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    val initialWallets = Wallets.browse.call(WalletBrowseRequest(), client).orThrow().items

                    assertThatInstance(initialWallets, "has a single wallet with correct parameters") {
                        it.size == 1 && it.single().paysFor == input.product.category &&
                                it.single().allocations.size == 1
                    }

                    assertThatInstance(initialWallets.single().allocations.single(), "has a valid allocation") {
                        it.balance == input.initialBalance &&
                                it.initialBalance == input.initialBalance &&
                                it.endDate == null
                    }

                    Accounting.charge.call(
                        bulkRequestOf(
                            ChargeWalletRequestItem(
                                owner,
                                input.units,
                                input.numberOfProducts,
                                input.product.toReference(),
                                createdUser.username,
                                "Test charge"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    val walletsAfterCharge = Wallets.browse.call(WalletBrowseRequest(), client).orThrow().items
                    val newAllocation = walletsAfterCharge.singleOrNull()?.allocations?.singleOrNull()
                        ?: error("newAllocation is null")

                    Out(newAllocation.balance)
                }

                listOf(true, false).forEach { isProject ->
                    val name = if (isProject) "Project" else "User"

                    fun balanceWasDeducted(input: In, output: Out) {
                        assertEquals(
                            input.initialBalance - (input.product.pricePerUnit * input.units * input.numberOfProducts),
                            output.newBalance
                        )
                    }

                    case("$name with enough credits") {
                        input(In(isProject, 1000.DKK, 1))
                        check { balanceWasDeducted(input, output) }
                    }

                    case("$name with over-charge") {
                        input(In(isProject, 10.DKK, 1_000_000_000))
                        check {
                            assertThatInstance(output.newBalance) { it < 0 }
                        }
                    }

                    case("$name with negative units") {
                        input(In(isProject, 100.DKK, -100))
                        expectStatusCode(HttpStatusCode.BadRequest)
                    }

                    case("$name with negative number of products") {
                        input(In(isProject, 100.DKK, 1, -1))
                        expectStatusCode(HttpStatusCode.BadRequest)
                    }

                    case("$name with no products involved") {
                        input(In(isProject, 100.DKK, 0))
                        expectStatusCode(HttpStatusCode.BadRequest)
                    }
                }
            }
        }

        run {
            class In(
                val rootBalance: Long,
                val chainFromRoot: List<Allocation>,
                val units: Long,
                val numberOfProducts: Long = 1,
                val product: Product = sampleCompute,
                val skipCreationOfLeaf: Boolean = false
            )

            class ChargeOutput(
                val balancesFromRoot: List<Long>,
                val transactionsFromRoot: List<List<Transaction>>
            )

            class CheckOutput(
                val hasEnoughCredits: Boolean
            )

            suspend fun prepare(input: In): List<AllocationResult> {
                return prepareProjectChain(
                    input.rootBalance, input.chainFromRoot, input.product.category,
                    input.skipCreationOfLeaf
                )
            }

            test<In, ChargeOutput>("Deposit and charge") {
                execute {
                    val leaves = prepare(input)

                    Accounting.charge.call(
                        bulkRequestOf(
                            ChargeWalletRequestItem(
                                leaves.last().owner,
                                input.units,
                                input.numberOfProducts,
                                input.product.toReference(),
                                leaves.last().username,
                                "Test charge"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    ChargeOutput(
                        leaves.map {
                            findWallet(it.client, input.product.category)?.allocations?.singleOrNull()?.balance ?: 0L
                        },
                        leaves.map {
                            Transactions.browse.call(
                                TransactionsBrowseRequest(input.product.category.name, input.product.category.provider),
                                it.client
                            ).orThrow().items
                        }
                    )
                }

                fun balanceWasDeducted(input: In, output: ChargeOutput) {
                    val paymentRequired = input.product.pricePerUnit * input.units * input.numberOfProducts

                    println("Payment required: $paymentRequired")

                    output.balancesFromRoot.forEachIndexed { index, balance ->
                        assertEquals(
//                            if (index == output.balancesFromRoot.lastIndex) {
//                                max(0, input.chainFromRoot[index].amount - paymentRequired)
//                            } else {
                                input.chainFromRoot[index].amount - paymentRequired,
//                            },
                            balance,
                            "New balance of $index should match expected value"
                        )
                    }

                    output.transactionsFromRoot.forEachIndexed { index, transactions ->
                        val deposits = transactions.filterIsInstance<Transaction.Deposit>()
                        assertThatInstance(deposits, "Should only contain a single deposit ($index)") {
                            it.size == 1
                        }

                        val deposit = deposits.single()
                        assertThatPropertyEquals(
                            deposit,
                            { it.resolvedCategory },
                            input.product.category,
                            "resolvedCategory[$index]"
                        )
                        assertThatPropertyEquals(
                            deposit,
                            { it.change },
                            input.chainFromRoot[index].amount,
                            "change[$index]"
                        )
                        assertThatInstance(deposit, "startDate[$index]") { it.startDate != null }
                        assertThatPropertyEquals(deposit, { it.endDate }, null, "endDate[$index]")
                    }

                    output.transactionsFromRoot.forEachIndexed { index, transactions ->
                        val charges = transactions.filterIsInstance<Transaction.Charge>()
                        assertThatInstance(charges, "Should only contain a single charge ($index)") {
                            it.size == 1
                        }

                        val expectedNewBalance = input.chainFromRoot[index].amount - paymentRequired
                        val expectedChange = expectedNewBalance - input.chainFromRoot[index].amount
                        val charge = charges.single()
                        assertThatPropertyEquals(charge, { it.units }, input.units, "units[$index]")
                        assertThatPropertyEquals(
                            charge,
                            { it.numberOfProducts },
                            input.numberOfProducts,
                            "numberOfProducts[$index]"
                        )
                        assertThatPropertyEquals(charge, { it.productId }, input.product.name, "productId[$index]")
                        assertThatPropertyEquals(
                            charge,
                            { it.resolvedCategory },
                            input.product.category,
                            "resolvedCategory[$index]"
                        )
                        assertThatPropertyEquals(charge, { it.change }, expectedChange, "change[$index]")
                    }
                }

                listOf(true, false).forEach { isProject ->
                    val name = if (isProject) "project" else "user"

                    (1..3).forEach { nlevels ->
                        case("$nlevels-level(s) of $name") {
                            input(
                                In(
                                    rootBalance = 1000.DKK,
                                    chainFromRoot = (0 until nlevels).map { Allocation(isProject, 1000.DKK) },
                                    units = 1L
                                )
                            )

                            check { balanceWasDeducted(input, output) }
                        }
                    }
                }

                (3..6).forEach { nlevels ->
                    case("$nlevels-levels of mixed users/projects") {
                        input(
                            In(
                                rootBalance = 1000.DKK,
                                chainFromRoot = (0 until nlevels).map { Allocation(it % 2 == 0, 1000.DKK) },
                                units = 1L
                            )
                        )

                        check { balanceWasDeducted(input, output) }
                    }
                }

                case("negative units") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("negative numberOfProducts") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = 1L,
                            numberOfProducts = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("very large charge") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = Int.MAX_VALUE.toLong() * 2
                        )
                    )

                    check { balanceWasDeducted(input, output) }
                }

                case("Charge missing payer") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = (0 until 3).map { Allocation(true, 1000.DKK) },
                            units = 100L,
                            skipCreationOfLeaf = true
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }
            }

            test<In, CheckOutput>("Deposit and check") {
                execute {
                    val leaves = prepare(input)
                    CheckOutput(
                        Accounting.check.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    leaves.last().owner,
                                    input.units,
                                    input.numberOfProducts,
                                    input.product.toReference(),
                                    leaves.last().username,
                                    "Test charge"
                                )
                            ),
                            serviceClient
                        ).orThrow().responses.single()
                    )
                }

                listOf(true, false).forEach { isProject ->
                    val name = if (isProject) "project" else "user"

                    (1..3).forEach { nlevels ->
                        case("$nlevels-level(s) of $name") {
                            input(
                                In(
                                    rootBalance = 1000.DKK,
                                    chainFromRoot = (0 until nlevels).map { Allocation(isProject, 1000.DKK) },
                                    units = 1L
                                )
                            )

                            check { assertEquals(true, output.hasEnoughCredits) }
                        }
                    }
                }

                (3..6).forEach { nlevels ->
                    case("$nlevels-levels of mixed users/projects") {
                        input(
                            In(
                                rootBalance = 1000.DKK,
                                chainFromRoot = (0 until nlevels).map { Allocation(it % 2 == 0, 1000.DKK) },
                                units = 1L
                            )
                        )

                        check { assertEquals(true, output.hasEnoughCredits) }
                    }
                }

                case("negative units") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("negative numberOfProducts") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = 1L,
                            numberOfProducts = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("very large charge") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = Int.MAX_VALUE.toLong() * 2
                        )
                    )

                    check { assertEquals(false, output.hasEnoughCredits) }
                }

                case("Charge missing payer") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = (0 until 3).map { Allocation(true, 1000.DKK) },
                            units = 100L,
                            skipCreationOfLeaf = true
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("Missing payment in a non-leaf") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(
                                Allocation(true, 1000.DKK),
                                Allocation(true, 0.DKK),
                                Allocation(true, 1000.DKK)
                            ),
                            units = 100L
                        )
                    )

                    check { assertEquals(false, output.hasEnoughCredits) }
                }
            }
        }

        run {
            class Charge(val index: Int, val amount: Long)

            class In(
                val chainFromRoot: List<Allocation>,
                val updateIndex: Int,
                val newBalance: Long,
                val newStartDate: Long,
                val newEndDate: Long?,
                val product: Product = sampleCompute,
                val charges: List<Charge> = emptyList(),
            )

            class Out(
                val allocationsFromRoot: List<WalletAllocation>,
                val transactionsFromRoot: List<List<Transaction>>,
            )

            test<In, Out>("Update allocations") {
                execute {
                    val leaves = prepareProjectChain(10_000.DKK, input.chainFromRoot, input.product.category)
                    val alloc =
                        findWallet(leaves[input.updateIndex].client, input.product.category)!!.allocations.single()

                    for (charge in input.charges) {
                        val leaf = leaves[charge.index]
                        Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    payer = leaf.owner,
                                    units = charge.amount,
                                    numberOfProducts = 1L,
                                    product = input.product.toReference(),
                                    performedBy = leaf.username,
                                    description = "Charge"
                                )
                            ),
                            serviceClient
                        ).orThrow()
                    }

                    val request = bulkRequestOf(
                        UpdateAllocationRequestItem(
                            alloc.id,
                            input.newBalance,
                            input.newStartDate,
                            input.newEndDate,
                            "Change"
                        )
                    )
                    Accounting.updateAllocation.call(request, leaves[max(0, input.updateIndex - 1)].client).orThrow()
                    assertThatInstance(
                        Accounting.updateAllocation.call(request, leaves.last().client),
                        "Should not be able to update this allocation"
                    ) { it.statusCode.value in 400..499 }

                    Out(
                        leaves.map {
                            findWallet(it.client, input.product.category)!!.allocations.single()
                        },
                        leaves.map {
                            Transactions.browse.call(
                                TransactionsBrowseRequest(
                                    input.product.category.name,
                                    input.product.category.provider
                                ),
                                it.client
                            ).orThrow().items
                        }
                    )
                }

                // NOTE(Dan): We don't store millisecond precision hence this weird calculation
                val initialStartDate = ((Time.now() + (1000 * 60 * 60 * 24 * 7)) / 1000) * 1000

                case("Update differential") {
                    input(In(
                        chainFromRoot = listOf(
                            Allocation(true, 1_000_000),
                            Allocation(true, 1000),
                            Allocation(true, 1000)
                        ),
                        updateIndex = 1,
                        newBalance = 10_000,
                        newStartDate = initialStartDate,
                        newEndDate = null,
                        product = sampleStorageDifferential,
                        charges = listOf(Charge(1, 100L), Charge(2, 200L))
                    ))

                    check {
                        assertEquals(10_000, output.allocationsFromRoot[1].initialBalance)
                        assertEquals(1000, output.allocationsFromRoot[2].initialBalance)

                        assertEquals(9700, output.allocationsFromRoot[1].balance)
                        assertEquals(9900, output.allocationsFromRoot[1].localBalance)

                        assertEquals(800, output.allocationsFromRoot[2].balance)
                        assertEquals(800, output.allocationsFromRoot[2].localBalance)
                    }
                }

                listOf(true, false).forEach { isProject ->
                    val name = if (isProject) "project" else "user"
                    (2..4).forEach { nlevels ->
                        (1 until nlevels).forEach { updateIdx ->
                            case("${nlevels}-levels of $name (updateIdx = $updateIdx)") {
                                val initialBalance = 1000.DKK
                                val newBalance = 500.DKK

                                input(
                                    In(
                                        (0 until nlevels).map { Allocation(isProject, initialBalance, initialStartDate) },
                                        updateIdx,
                                        newBalance,
                                        initialStartDate,
                                        null
                                    )
                                )

                                check {
                                    output.allocationsFromRoot.forEachIndexed { idx, alloc ->
                                        if (idx == updateIdx) {
                                            assertThatPropertyEquals(alloc, { it.balance }, newBalance, "balance")
                                            assertThatPropertyEquals(alloc, { it.initialBalance }, newBalance,
                                                "initialBalance")
                                        } else {
                                            assertThatPropertyEquals(alloc, { it.balance }, initialBalance, "balance")
                                            assertThatPropertyEquals(alloc, { it.initialBalance }, initialBalance,
                                                "initialBalance")
                                        }
                                    }
                                }

                                check {
                                    output.transactionsFromRoot.forEachIndexed { index, transactions ->
                                        val updates = transactions.filterIsInstance<Transaction.AllocationUpdate>()
                                        if (index != updateIdx) {
                                            assertThatInstance(updates, "should be empty") { it.isEmpty() }
                                        } else {
                                            assertThatInstance(updates, "has only one update") { it.size == 1 }
                                            val update = updates.single()
                                            assertThatPropertyEquals(update, { it.change }, -(initialBalance - newBalance))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                case("update self should fail") {
                    input(
                        In(
                            listOf(Allocation(true, 1000.DKK, initialStartDate)),
                            0,
                            1_000_000.DKK,
                            initialStartDate,
                            null
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("update affects children allocation period") {
                    val newEndDate = initialStartDate + (1000 * 60 * 60 * 24)

                    input(
                        In(
                            listOf(
                                Allocation(true, 1000.DKK, initialStartDate),
                                Allocation(true, 1000.DKK, initialStartDate),
                                Allocation(true, 1000.DKK, initialStartDate),
                                Allocation(true, 1000.DKK, initialStartDate),
                            ),
                            1,
                            1000.DKK,
                            initialStartDate,
                            newEndDate
                        )
                    )

                    check {
                        output.allocationsFromRoot.drop(1).forEach { alloc ->
                            assertThatPropertyEquals(alloc, { it.startDate }, initialStartDate, "startDate")
                            assertThatPropertyEquals(alloc, { it.endDate }, newEndDate, "endDate")
                        }

                        output.transactionsFromRoot.forEachIndexed { index, transactions ->
                            if (index >= 1) {
                                val updates = transactions.filterIsInstance<Transaction.AllocationUpdate>()
                                assertThatInstance(updates, "has only one update") { it.size == 1 }
                                val update = updates.single()
                                assertThatPropertyEquals(update, { it.change }, 0)
                                assertThatPropertyEquals(update, { it.startDate }, initialStartDate)
                                assertThatPropertyEquals(update, { it.endDate }, newEndDate)
                            }
                        }
                    }
                }
            }
        }

        run {
            class In(
                val rootBalance: Long,
                val chainFromRoot: List<Allocation>,
                val chargesFromRoot: List<List<Long?>>,
                val breadth: Int = 1,
            )

            class Out(
                val wallets: List<Wallet>
            )

            test<In, Out>("Differential quota products") {
                execute {
                    val leaves = prepareProjectChain(input.rootBalance, input.chainFromRoot,
                        sampleStorageDifferential.category, breadth = input.breadth)

                    for (iteration in input.chargesFromRoot) {
                        val requests = iteration.mapIndexedNotNull { idx, charge ->
                            if (charge == null) return@mapIndexedNotNull null
                            val leaf = leaves[idx]
                            ChargeWalletRequestItem(
                                leaf.owner,
                                charge,
                                1,
                                sampleStorageDifferential.toReference(),
                                leaf.username,
                                "Charging..."
                            )
                        }

                        Accounting.charge.call(BulkRequest(requests), serviceClient).orThrow()
                    }

                    Out(
                        leaves.map {
                            findWallet(it.client, sampleStorageDifferential.category)!!
                        }
                    )
                }

                case("Single allocation, no over-charge") {
                    input(In(
                        rootBalance = 1000L,
                        chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                        chargesFromRoot = listOf(listOf(null, 100L))
                    ))

                    check {
                        assertEquals(900, output.wallets[0].allocations[0].balance)
                        assertEquals(1000, output.wallets[0].allocations[0].localBalance)

                        assertEquals(900, output.wallets[1].allocations[0].balance)
                        assertEquals(900, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Single allocation, multiple charges, no over-charge") {
                    input(In(
                        rootBalance = 1000L,
                        chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                        chargesFromRoot = listOf(listOf(null, 100L), listOf(50L, 50L))
                    ))

                    check {
                        assertEquals(900, output.wallets[0].allocations[0].balance)
                        assertEquals(950, output.wallets[0].allocations[0].localBalance)

                        assertEquals(950, output.wallets[1].allocations[0].balance)
                        assertEquals(950, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Multiple allocations, spread without over-charge") {
                    input(In(
                        rootBalance = 1000L,
                        chainFromRoot = listOf(Allocation(true, 4000L), Allocation(true, 1500L)),
                        chargesFromRoot = listOf(listOf(null, 2000L)),
                        breadth = 2,
                    ))

                    check {
                        assertEquals(2500, output.wallets[0].allocations[0].balance)
                        assertEquals(4000, output.wallets[0].allocations[0].localBalance)
                        assertEquals(3500, output.wallets[0].allocations[1].balance)
                        assertEquals(4000, output.wallets[0].allocations[1].localBalance)

                        assertEquals(0, output.wallets[1].allocations[0].balance)
                        assertEquals(0, output.wallets[1].allocations[0].localBalance)
                        assertEquals(1000, output.wallets[1].allocations[1].balance)
                        assertEquals(1000, output.wallets[1].allocations[1].localBalance)
                    }
                }

                case("Single allocation, with over-charge in leaf") {
                    input(In(
                        rootBalance = 1_000_000,
                        chainFromRoot = listOf(Allocation(true, 10_000L), Allocation(true, 4000)),
                        chargesFromRoot = listOf(listOf(null, 1), listOf(null, 10000), listOf(null, 5000L))
                    ))

                    check {
                        assertEquals(5000, output.wallets[0].allocations[0].balance)
                        assertEquals(10_000, output.wallets[0].allocations[0].localBalance)

                        assertEquals(-1000, output.wallets[1].allocations[0].balance)
                        assertEquals(-1000, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Multiple allocation, with over-charge in leaf") {
                    input(In(
                        rootBalance = 1_000_000,
                        chainFromRoot = listOf(Allocation(true, 10_000L), Allocation(true, 4000)),
                        chargesFromRoot = listOf(listOf(null, 1), listOf(null, 100_000), listOf(null, 10_000L)),
                        breadth = 2
                    ))

                    check {
                        assertEquals(4000, output.wallets[0].allocations[0].balance)
                        assertEquals(10_000, output.wallets[0].allocations[0].localBalance)
                        assertEquals(6000, output.wallets[0].allocations[1].balance)
                        assertEquals(10_000, output.wallets[0].allocations[1].localBalance)

                        assertEquals(-2000, output.wallets[1].allocations[0].balance)
                        assertEquals(-2000, output.wallets[1].allocations[0].localBalance)
                        assertEquals(0, output.wallets[1].allocations[1].balance)
                        assertEquals(0, output.wallets[1].allocations[1].localBalance)

                    }
                }
            }
        }
    }
}
