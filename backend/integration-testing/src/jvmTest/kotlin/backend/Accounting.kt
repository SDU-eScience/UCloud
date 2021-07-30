package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Accounting
import dk.sdu.cloud.accounting.api.ChargeWalletRequestItem
import dk.sdu.cloud.accounting.api.DepositToWalletRequestItem
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.RootDepositRequestItem
import dk.sdu.cloud.accounting.api.Transaction
import dk.sdu.cloud.accounting.api.Transactions
import dk.sdu.cloud.accounting.api.TransactionsBrowseRequest
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatProperty
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

class AccountingTest : IntegrationTest() {
    override fun defineTests() {
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
                        input(In(isProject, 10.DKK, 20.DKK))
                        check {
                            assertEquals(0, output.newBalance)
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
            class Allocation(val isProject: Boolean, val amount: Long)

            class In(
                val rootBalance: Long,
                val chainFromRoot: List<Allocation>,
                val units: Long,
                val numberOfProducts: Long = 1,
                val product: Product = sampleCompute,
                val skipCreationOfLeaf: Boolean = false
            )

            class Out(
                val balancesFromRoot: List<Long>,
                val transactionsFromRoot: List<List<Transaction>>
            )

            test<In, Out>("Deposit and charge") {
                execute {
                    val rootPi = createUser()
                    val rootProject = initializeRootProject(rootPi.username, amount = input.rootBalance)

                    val irrelevantUser = createUser()

                    data class UserAndClient(
                        val username: String, val projectId: String?,
                        val client: AuthenticatedClient, val balance: Long
                    ) {
                        val owner: WalletOwner = if (projectId == null) {
                            WalletOwner.User(username)
                        } else {
                            WalletOwner.Project(projectId)
                        }
                    }

                    val leaves = ArrayList<UserAndClient>()
                    for ((index, allocOwner) in input.chainFromRoot.withIndex()) {
                        if (allocOwner.isProject) {
                            val user = createUser()
                            val project = Projects.create.call(
                                CreateProjectRequest("P$index", principalInvestigator = user.username),
                                serviceClient
                            ).orThrow()

                            leaves.add(
                                UserAndClient(
                                    user.username,
                                    project.id,
                                    user.client.withProject(project.id),
                                    allocOwner.amount
                                )
                            )
                        } else {
                            val user = createUser()
                            leaves.add(UserAndClient(user.username, null, user.client, allocOwner.amount))
                        }
                    }

                    var previousAllocation: String =
                        findProjectWallet(rootProject, rootPi.client, input.product.category)!!.allocations.single().id
                    var previousPi: AuthenticatedClient = rootPi.client
                    val expectedAllocationPath = arrayListOf(previousAllocation)
                    for ((index, leaf) in leaves.withIndex()) {
                        if (index == leaves.lastIndex && input.skipCreationOfLeaf) break

                        val request = bulkRequestOf(
                            DepositToWalletRequestItem(
                                leaf.owner,
                                previousAllocation,
                                leaf.balance,
                                "Transfer"
                            )
                        )
                        Accounting.deposit.call(request, previousPi).orThrow()

                        assertThatInstance(
                            Accounting.deposit.call(request, irrelevantUser.client),
                            "Should fail because they are not a part of the project"
                        ) { it.statusCode.value in 400..499 }

                        val alloc = findWallet(leaf.client, input.product.category)!!.allocations.single()
                        previousAllocation = alloc.id
                        previousPi = leaf.client

                        expectedAllocationPath.add(alloc.id)
                        assertEquals(alloc.allocationPath, expectedAllocationPath)
                        assertEquals(alloc.balance, leaf.balance)
                        assertEquals(alloc.initialBalance, leaf.balance)
                    }

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

                    Out(
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

                fun balanceWasDeducted(input: In, output: Out) {
                    val paymentRequired = input.product.pricePerUnit * input.units * input.numberOfProducts

                    output.balancesFromRoot.forEachIndexed { index, balance ->
                        assertEquals(
                            max(0, input.chainFromRoot[index].amount - paymentRequired),
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
                        assertThatPropertyEquals(deposit, { it.resolvedCategory }, input.product.category, "resolvedCategory[$index]")
                        assertThatPropertyEquals(deposit, { it.change }, input.chainFromRoot[index].amount, "change[$index]")
                        assertThatInstance(deposit, "startDate[$index]") { it.startDate != null }
                        assertThatPropertyEquals(deposit, { it.endDate }, null, "endDate[$index]")
                    }

                    output.transactionsFromRoot.forEachIndexed { index, transactions ->
                        val charges = transactions.filterIsInstance<Transaction.Charge>()
                        assertThatInstance(charges, "Should only contain a single charge ($index)") {
                            it.size == 1
                        }

                        val expectedNewBalance = max(0, input.chainFromRoot[index].amount - paymentRequired)
                        val expectedChange = expectedNewBalance - input.chainFromRoot[index].amount
                        val charge = charges.single()
                        assertThatPropertyEquals(charge, { it.units }, input.units, "units[$index]")
                        assertThatPropertyEquals(charge, { it.numberOfProducts }, input.numberOfProducts, "numberOfProducts[$index]")
                        assertThatPropertyEquals(charge, { it.productId }, input.product.name, "productId[$index]")
                        assertThatPropertyEquals(charge, { it.resolvedCategory }, input.product.category, "resolvedCategory[$index]")
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
        }
    }

    /*

    @Test
    fun `test funds are checked locally`() = t {
        val initialBalance = 1000.DKK
        val charge = 500.DKK
        val r = initializeNormalProject(initializeRootProject(), amount = initialBalance)
        val parents = arrayListOf(r.projectId)
        val category = sampleCompute.category

        repeat(5) {
            val element = Projects.create.call(
                CreateProjectRequest("T$it", parents.last()),
                r.piClient
            ).orThrow().id
            parents.add(element)

            Wallets.setBalance.call(
                SetBalanceRequest(Wallet(element, WalletOwnerType.PROJECT, category), 0L, initialBalance),
                r.piClient
            ).orThrow()
        }

        Wallets.setBalance.call(
            SetBalanceRequest(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance, 0L),
            r.piClient
        ).orThrow()

        try {
            reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), charge)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }

    @Test
    fun `test funds are checked in ancestors`() = t {
        val initialBalance = 1000.DKK
        val r = initializeNormalProject(initializeRootProject(), amount = initialBalance)
        val parents = arrayListOf(r.projectId)
        val category = sampleCompute.category

        repeat(5) {
            val element = Projects.create.call(
                CreateProjectRequest("T$it", parents.last()),
                r.piClient
            ).orThrow().id
            parents.add(element)

            Wallets.setBalance.call(
                SetBalanceRequest(Wallet(element, WalletOwnerType.PROJECT, category), 0L, initialBalance),
                r.piClient
            ).orThrow()
        }

        Wallets.setBalance.call(
            SetBalanceRequest(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance,
                initialBalance * 10),
            r.piClient
        ).orThrow()

        try {
            reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), initialBalance * 2)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.PaymentRequired, ex.httpStatusCode)
        }
    }
     */
}
