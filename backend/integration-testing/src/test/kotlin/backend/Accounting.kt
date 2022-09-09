package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Accounting
import dk.sdu.cloud.accounting.api.ChargeWalletRequestItem
import dk.sdu.cloud.accounting.api.DepositToWalletRequestItem
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.RootDepositRequestItem
import dk.sdu.cloud.accounting.api.Transaction
import dk.sdu.cloud.accounting.api.Transactions
import dk.sdu.cloud.accounting.api.TransactionsBrowseRequest
import dk.sdu.cloud.accounting.api.TransferToWalletRequestItem
import dk.sdu.cloud.accounting.api.UpdateAllocationRequestItem
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletAllocation
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.db
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.project.api.AcceptInviteRequest
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.InviteRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.TransferPiRoleRequest
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import kotlinx.serialization.decodeFromString
import java.util.*
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

class AccountingTest : IntegrationTest() {
    override fun defineTests() {

        run {
            class In(
                val useProjectChain: Boolean,
                val transactionId: String,
                val useSingleProject: Boolean,
                val singleProjectMultipleAllocations: Boolean = false,
                val duplicateCharge: Boolean = false
            )

            class Out(
                val transactions: List<Transaction>,
                val firstChargeResults: List<Boolean>,
                val secondChargeResults: List<Boolean>
            )
            test<In, Out>("Transaction tests") {
                execute {
                    var firstChargeResults = emptyList<Boolean>()
                    var secondChargeResults = emptyList<Boolean>()

                    createSampleProducts()

                    if (input.useProjectChain) {
                        val projectAllocations = prepareProjectChain(
                            10000.DKK,
                            listOf(
                                Allocation(true, 5000.DKK),
                                Allocation(true, 2000.DKK),
                                Allocation(true, 1000.DKK)
                            ),
                            sampleCompute.category,
                            serviceClient = UCloudLauncher.serviceClient
                        )

                        val walletOwner = projectAllocations.last().owner
                        val user = projectAllocations.last().username
                        firstChargeResults = Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    walletOwner,
                                    5,
                                    1,
                                    sampleCompute.toReference(),
                                    user,
                                    "Charge for job",
                                    input.transactionId
                                )
                            ),
                            serviceClient
                        ).orThrow().responses

                        projectAllocations.last().client
                    }
                    if (input.useSingleProject) {
                        val user1 = createUser("user1")
                        val projectId = initializeRootProject(
                            user1.username,
                            true,
                            1000.DKK
                        )

                        val walletOwner = WalletOwner.Project(projectId)

                        if (input.singleProjectMultipleAllocations) {
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        walletOwner,
                                        1000.DKK,
                                        "deposit",
                                        transactionId = "extraDeposit"
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        }

                        firstChargeResults = Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    walletOwner,
                                    if (input.singleProjectMultipleAllocations) 15000 else 5000,
                                    1,
                                    sampleCompute.toReference(),
                                    user1.username,
                                    "charging for job",
                                    input.transactionId
                                )
                            ),
                            serviceClient
                        ).orThrow().responses
                        if (input.duplicateCharge) {
                            secondChargeResults = Accounting.charge.call(
                                bulkRequestOf(
                                    ChargeWalletRequestItem(
                                        walletOwner,
                                        2000,
                                        1,
                                        sampleCompute.toReference(),
                                        user1.username,
                                        "I can do this again?",
                                        input.transactionId
                                    )
                                ),
                                serviceClient
                            ).orThrow().responses
                        }
                    }

                    val transactions = db.withSession { session ->
                        session.sendQuery(
                            """
                                select accounting.transaction_to_json(t, p, pc)
                                from
                                    accounting.transactions t join
                                    accounting.wallet_allocations alloc on t.affected_allocation_id = alloc.id join
                                    accounting.wallets w on alloc.associated_wallet = w.id join
                                    accounting.product_categories pc on w.category = pc.id join
                                    accounting.wallet_owner wo on w.owned_by = wo.id left join
                                    project.project_members pm on wo.project_id = pm.project_id left join
                                    accounting.products p on pc.id = p.category and t.product_id = p.id 
                            """
                        ).rows.map { defaultMapper.decodeFromString<Transaction>(it.getString(0)!!) }
                    }

                    Out(
                        transactions = transactions,
                        firstChargeResults = firstChargeResults,
                        secondChargeResults = secondChargeResults
                    )
                }
                case("single allocation") {
                    input(
                        In(
                            useProjectChain = false,
                            transactionId = "123456",
                            useSingleProject = true
                        )
                    )
                    check {
                        val charges = output.transactions.filter { it is Transaction.Charge }
                        println(charges)
                        assertEquals(1, charges.size)
                        charges.forEach { charge ->
                            assertEquals(charge.initialTransactionId.substringAfterLast('-'), input.transactionId)
                            assertEquals(charge.transactionId.substringAfterLast('-'), input.transactionId)
                        }
                    }
                }

                case("multiple allocation same depth") {
                    input(
                        In(
                            useProjectChain = false,
                            transactionId = "123456",
                            useSingleProject = true,
                            singleProjectMultipleAllocations = true
                        )
                    )
                    check {
                        val charges = output.transactions.filter { it is Transaction.Charge }
                        println(charges)
                        assertEquals(2, charges.size)
                        charges.forEach { charge ->
                            assertEquals(charge.initialTransactionId.substringAfterLast('-'), input.transactionId)
                            assertEquals(charge.transactionId.substringAfterLast('-'), input.transactionId)
                        }
                    }
                }

                case("single allocation with parents") {
                    input(
                        In(
                            useProjectChain = true,
                            transactionId = "123456",
                            useSingleProject = false
                        )
                    )
                    check {
                        val charges = output.transactions.filter { it is Transaction.Charge }
                        println(charges)
                        assertEquals(4, charges.size)
                        charges.forEach { charge ->
                            assertEquals(charge.initialTransactionId.substringAfterLast('-'), input.transactionId)
                            assertTrue { charges.filter { charge.transactionId == it.transactionId }.size == 1 }
                        }

                    }
                }

                case("same transactionId") {
                    input(
                        In(
                            useProjectChain = false,
                            transactionId = "123456",
                            useSingleProject = true,
                            duplicateCharge = true
                        )
                    )
                    check {
                        val firstCharge = output.firstChargeResults.firstOrNull()
                        assertNotNull(firstCharge)
                        assertTrue(firstCharge!!)
                        val secondCharge = output.secondChargeResults.firstOrNull()
                        assertNotNull(secondCharge)
                        assertTrue(secondCharge!!)
                    }
                }
            }
        }

        run {
            class In(
                val product: Product,
                val expectedChargeResults: List<Boolean>
            )

            class Out(
                val wallets: PageV2<Wallet>
            )

            test<In, Out>("Single product test") {
                execute {
                    createProvider("ucloud")
                    Products.create.call(
                        bulkRequestOf(input.product),
                        serviceClient
                    ).orThrow()
                    val user1 = createUser("user1")
                    val targetOwner = WalletOwner.User(user1.username)

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                input.product.category,
                                targetOwner,
                                10000.DKK,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    val results = Accounting.charge.call(
                        bulkRequestOf(
                            ChargeWalletRequestItem(
                                targetOwner,
                                10L,
                                1L,
                                input.product.toReference(),
                                user1.username,
                                "test charging",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow().responses

                    assertThatInstance(input.expectedChargeResults, "Charge behaving correctly") {
                        it == results
                    }

                    val wallets = Wallets.browse.call(
                        WalletBrowseRequest(),
                        user1.client
                    ).orThrow()

                    Out(wallets)
                }

                case("zero price per unit") {
                    input(
                        In(
                            sampleCompute.copy(pricePerUnit = 0),
                            expectedChargeResults = listOf(true)
                        )
                    )
                    check {
                        assertEquals(
                            output.wallets.items.singleOrNull()?.allocations?.singleOrNull()?.localBalance,
                            10000.DKK
                        )
                    }
                }
            }
        }

        run {
            class In(
                val products: List<Product>,
                val providers: List<String>,
                val user: String,
                val productsToCharge: List<Product>,
                val chargeAmount: Long = 10,
                val walletAmount: Long = 10000.DKK,
                val createWallet: Boolean = false
            )

            class Out(
                val allocation: WalletAllocation?,
                val chargeResults: List<Boolean>
            )

            test<In, Out>("Free to use products") {
                execute {

                    input.providers.forEach {
                        createProvider(it)
                    }
                    createSampleProducts()

                    Products.create.call(
                        BulkRequest(input.products),
                        serviceClient
                    ).orThrow()

                    val user1 = createUser(input.user)
                    val targetOwner = WalletOwner.User(user1.username)

                    if (input.createWallet) {
                        Accounting.rootDeposit.call(
                            bulkRequestOf(
                                RootDepositRequestItem(
                                    sampleCompute.category,
                                    targetOwner,
                                    input.walletAmount,
                                    "Initial deposit",
                                    transactionId = UUID.randomUUID().toString()
                                )
                            ),
                            serviceClient
                        ).orThrow()
                    }

                    val chargeRequests = mutableListOf<ChargeWalletRequestItem>()
                    input.productsToCharge.forEach {
                        chargeRequests.add(
                            ChargeWalletRequestItem(
                                targetOwner,
                                input.chargeAmount,
                                1L,
                                it.toReference(),
                                user1.username,
                                "test charging",
                                transactionId = UUID.randomUUID().toString()
                            )
                        )
                    }

                    Accounting.check.call(
                        BulkRequest(chargeRequests),
                        serviceClient
                    ).orThrow()

                    val chargeResults = Accounting.charge.call(
                        BulkRequest(chargeRequests),
                        serviceClient
                    ).orThrow().responses

                    val wallets = Wallets.browse.call(
                        WalletBrowseRequest(),
                        user1.client
                    ).orThrow()

                    val allocation = wallets.items.singleOrNull()?.allocations?.singleOrNull()
                    Out(allocation, chargeResults)
                }

                case("charge free to use") {
                    val freeCompute = Product.Compute(
                        "freeCompute",
                        1,
                        ProductCategoryId("ucloud", "ucloud"),
                        "new description",
                        freeToUse = true
                    )
                    input(
                        In(
                            products = listOf(
                                freeCompute
                            ),
                            providers = listOf(
                                "ucloud"
                            ),
                            user = "user1",
                            productsToCharge = listOf(
                                freeCompute
                            )
                        )
                    )
                    check {
                        assertNull(output.allocation)
                        assertEquals(listOf(true), output.chargeResults)
                    }
                }
                case("charge free to use multiple items") {
                    val freeCompute = Product.Compute(
                        "freeCompute",
                        1,
                        ProductCategoryId("ucloud", "ucloud"),
                        "new description",
                        freeToUse = true
                    )
                    val freeQuota = Product.Storage(
                        "freeQuota",
                        1,
                        ProductCategoryId("ceph-quota", "ucloud"),
                        "new description",
                        freeToUse = true
                    )
                    input(
                        In(
                            products = listOf(
                                freeCompute,
                                freeQuota
                            ),
                            providers = listOf(
                                "ucloud"
                            ),
                            user = "user1",
                            productsToCharge = listOf(
                                freeCompute,
                                freeQuota
                            )
                        )
                    )

                    check {
                        assertNull(output.allocation)
                        assertEquals(listOf(true, true), output.chargeResults)
                    }
                }

                case("charge mixed items (free and compute)") {
                    val freeCompute = Product.Compute(
                        "freeCompute",
                        1,
                        ProductCategoryId("ucloud", "ucloud"),
                        "new description",
                        freeToUse = true
                    )
                    val freeQuota = Product.Storage(
                        "freeQuota",
                        1,
                        ProductCategoryId("ceph-quota", "ucloud"),
                        "new description",
                        freeToUse = true
                    )
                    input(
                        In(
                            products = listOf(
                                freeCompute,
                                freeQuota
                            ),
                            providers = listOf(
                                "ucloud"
                            ),
                            user = "user1",
                            productsToCharge = listOf(
                                freeCompute,
                                freeQuota,
                                sampleCompute
                            ),
                            walletAmount = 100000.DKK,
                            chargeAmount = 1,
                            createWallet = true
                        )
                    )
                    check {
                        assertNotNull(output.allocation)
                        assertEquals(
                            input.walletAmount - (input.chargeAmount * sampleCompute.pricePerUnit),
                            output.allocation?.localBalance
                        )
                        assertEquals(listOf(true, true, true), output.chargeResults)
                    }

                    case("charge mixed items (not enough resources to charge)") {
                        val freeCompute = Product.Compute(
                            "freeCompute",
                            1,
                            ProductCategoryId("ucloud", "ucloud"),
                            "new description",
                            freeToUse = true
                        )
                        val freeQuota = Product.Storage(
                            "freeQuota",
                            1,
                            ProductCategoryId("ceph-quota", "ucloud"),
                            "new description",
                            freeToUse = true
                        )
                        input(
                            In(
                                products = listOf(
                                    freeCompute,
                                    freeQuota
                                ),
                                providers = listOf(
                                    "ucloud"
                                ),
                                user = "user1",
                                productsToCharge = listOf(
                                    freeCompute,
                                    freeQuota,
                                    sampleCompute
                                ),
                                walletAmount = 1000,
                                chargeAmount = 10,
                                createWallet = true
                            )
                        )
                        check {
                            assertNotNull(output.allocation)
                            assertEquals(listOf(true, true, false), output.chargeResults)
                            assertEquals(
                                input.walletAmount - (input.chargeAmount * sampleCompute.pricePerUnit),
                                output.allocation?.localBalance
                            )
                        }
                    }
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
                            RootDepositRequestItem(
                                sampleCompute.category,
                                input.owner,
                                100.DKK,
                                "Initial balance",
                                transactionId = UUID.randomUUID().toString()
                            )
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
                val periods: Long = 1,
                val product: Product = sampleCompute,
                val expectedChargeResults: List<Boolean>? = emptyList()
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
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
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

                    val chargeResponse = Accounting.charge.call(
                        bulkRequestOf(
                            ChargeWalletRequestItem(
                                owner,
                                input.units,
                                input.periods,
                                input.product.toReference(),
                                createdUser.username,
                                "Test charge",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow().responses

                    assertThatInstance(chargeResponse, "charges behaving as expected") {
                        it == input.expectedChargeResults
                    }

                    val walletsAfterCharge = Wallets.browse.call(WalletBrowseRequest(), client).orThrow().items
                    val newAllocation = walletsAfterCharge.singleOrNull()?.allocations?.singleOrNull()
                        ?: error("newAllocation is null")

                    Out(newAllocation.balance)
                }

                listOf(true, false).forEach { isProject ->
                    val name = if (isProject) "Project" else "User"

                    fun balanceWasDeducted(input: In, output: Out) {
                        assertEquals(
                            input.initialBalance - (input.product.pricePerUnit * input.units * input.periods),
                            output.newBalance
                        )
                    }

                    case("$name with enough credits") {
                        input(In(isProject, 1000.DKK, 1, expectedChargeResults = listOf(true)))
                        check { balanceWasDeducted(input, output) }
                    }

                    case("$name with over-charge") {
                        input(In(isProject, 10.DKK, 1_000_000_000, expectedChargeResults = listOf(false)))
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
                        input(In(isProject, 100.DKK, 0, expectedChargeResults = listOf(true)))
                        //expectStatusCode(HttpStatusCode.BadRequest)
                        check { }
                    }
                }
            }
        }

        run {
            class In(
                val rootBalance: Long,
                val chainFromRoot: List<Allocation>,
                val units: Long,
                val periods: Long = 1,
                val product: Product = sampleCompute,
                val skipCreationOfLeaf: Boolean = false,
                val expectedChargeResults: List<Boolean>? = emptyList(),
                val numberOfChargeIterations: Int = 1,
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
                    input.skipCreationOfLeaf,
                    serviceClient = UCloudLauncher.serviceClient
                )
            }

            test<In, ChargeOutput>("Deposit and charge") {
                execute {
                    val leaves = prepare(input)

                    repeat(input.numberOfChargeIterations) {
                        val chargeResults = Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    leaves.last().owner,
                                    input.units,
                                    input.periods,
                                    input.product.toReference(),
                                    leaves.last().username,
                                    "Test charge",
                                    transactionId = UUID.randomUUID().toString()
                                )
                            ),
                            serviceClient
                        ).orThrow().responses

                        assertThatInstance(chargeResults, "charges behaving as expected") {
                            it == input.expectedChargeResults
                        }
                    }

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
                    val paymentRequired = input.product.pricePerUnit * input.units * input.periods

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
                            { it.periods },
                            input.periods,
                            "periods[$index]"
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
                                    units = 1L,
                                    expectedChargeResults = listOf(true)
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
                                units = 1L,
                                expectedChargeResults = listOf(true)
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

                case("negative periods") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = 1L,
                            periods = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("very large charge") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = Int.MAX_VALUE.toLong() * 2,
                            expectedChargeResults = listOf(false)
                        )
                    )

                    check { balanceWasDeducted(input, output) }
                }

                case("Charge 0 on differential") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 100.DKK)),
                            product = sampleStorageDifferential,
                            units = 0,
                            expectedChargeResults = listOf(true),
                            numberOfChargeIterations = 10
                        )
                    )

                    check { }
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

                    expectStatusCode(HttpStatusCode.PaymentRequired)
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
                                    input.periods,
                                    input.product.toReference(),
                                    leaves.last().username,
                                    "Test charge",
                                    transactionId = UUID.randomUUID().toString()
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
                                    units = 1L,
                                    expectedChargeResults = listOf(true)
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
                                units = 1L,
                                expectedChargeResults = listOf(true)
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

                case("negative periods") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = 1L,
                            periods = -1L
                        )
                    )

                    expectStatusCode(HttpStatusCode.BadRequest)
                }

                case("very large charge") {
                    input(
                        In(
                            rootBalance = 1000.DKK,
                            chainFromRoot = listOf(Allocation(true, 1000.DKK)),
                            units = Int.MAX_VALUE.toLong() * 2,
                            expectedChargeResults = listOf(false)
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

                    expectStatusCode(HttpStatusCode.PaymentRequired)
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
                    val leaves = prepareProjectChain(10_000.DKK, input.chainFromRoot, input.product.category, serviceClient = UCloudLauncher.serviceClient)
                    val alloc =
                        findWallet(leaves[input.updateIndex].client, input.product.category)!!.allocations.single()

                    for (charge in input.charges) {
                        val leaf = leaves[charge.index]
                        Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    payer = leaf.owner,
                                    units = charge.amount,
                                    periods = 1L,
                                    product = input.product.toReference(),
                                    performedBy = leaf.username,
                                    description = "Charge",
                                    transactionId = UUID.randomUUID().toString()
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
                            "Change",
                            transactionId = UUID.randomUUID().toString()
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
                    input(
                        In(
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
                        )
                    )

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
                                        (0 until nlevels).map {
                                            Allocation(
                                                isProject,
                                                initialBalance,
                                                initialStartDate
                                            )
                                        },
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
                                            assertThatPropertyEquals(
                                                alloc, { it.initialBalance }, newBalance,
                                                "initialBalance"
                                            )
                                        } else {
                                            assertThatPropertyEquals(alloc, { it.balance }, initialBalance, "balance")
                                            assertThatPropertyEquals(
                                                alloc, { it.initialBalance }, initialBalance,
                                                "initialBalance"
                                            )
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
                                            assertThatPropertyEquals(
                                                update,
                                                { it.change },
                                                -(initialBalance - newBalance)
                                            )
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
                    val leaves = prepareProjectChain(
                        input.rootBalance, input.chainFromRoot,
                        sampleStorageDifferential.category, breadth = input.breadth,
                        serviceClient = UCloudLauncher.serviceClient
                    )

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
                                "Charging...",
                                transactionId = UUID.randomUUID().toString()
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
                    input(
                        In(
                            rootBalance = 1000L,
                            chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                            chargesFromRoot = listOf(listOf(null, 100L))
                        )
                    )

                    check {
                        assertEquals(900, output.wallets[0].allocations[0].balance)
                        assertEquals(1000, output.wallets[0].allocations[0].localBalance)

                        assertEquals(900, output.wallets[1].allocations[0].balance)
                        assertEquals(900, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Single allocation, multiple charges, no over-charge") {
                    input(
                        In(
                            rootBalance = 1000L,
                            chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                            chargesFromRoot = listOf(listOf(null, 100L), listOf(50L, 50L))
                        )
                    )

                    check {
                        assertEquals(900, output.wallets[0].allocations[0].balance)
                        assertEquals(950, output.wallets[0].allocations[0].localBalance)

                        assertEquals(950, output.wallets[1].allocations[0].balance)
                        assertEquals(950, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Multiple allocations, spread without over-charge") {
                    input(
                        In(
                            rootBalance = 1000L,
                            chainFromRoot = listOf(Allocation(true, 4000L), Allocation(true, 1500L)),
                            chargesFromRoot = listOf(listOf(null, 2000L)),
                            breadth = 2,
                        )
                    )

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
                    input(
                        In(
                            rootBalance = 1_000_000,
                            chainFromRoot = listOf(Allocation(true, 10_000L), Allocation(true, 4000)),
                            chargesFromRoot = listOf(listOf(null, 1), listOf(null, 10000), listOf(null, 5000L))
                        )
                    )

                    check {
                        assertEquals(5000, output.wallets[0].allocations[0].balance)
                        assertEquals(10_000, output.wallets[0].allocations[0].localBalance)

                        assertEquals(-1000, output.wallets[1].allocations[0].balance)
                        assertEquals(-1000, output.wallets[1].allocations[0].localBalance)
                    }
                }

                case("Multiple allocation, with over-charge in leaf") {
                    input(
                        In(
                            rootBalance = 1_000_000,
                            chainFromRoot = listOf(Allocation(true, 10_000L), Allocation(true, 4000)),
                            chargesFromRoot = listOf(listOf(null, 1), listOf(null, 100_000), listOf(null, 10_000L)),
                            breadth = 2
                        )
                    )

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
   
        run {
            class In(
                val rootBalance: Long,
                val chainFromRoot: List<Allocation>,
                val breadth: Int = 1,
                val chargeAmount: Long = 100,
                val extraAllocateAndCharge: Boolean = false
            )

            class Out(
                val wallets: List<Wallet>
            )

            test<In, Out>("Charge fix test") {
                execute {
                    val leaves = prepareProjectChain(
                        input.rootBalance, input.chainFromRoot,
                        sampleStorageDifferential.category, breadth = input.breadth,
                        serviceClient = UCloudLauncher.serviceClient
                    )

                    val subproject = leaves.first()
                    val leaf = leaves.last()

                    val request1 = ChargeWalletRequestItem(
                        leaf.owner,
                        input.chargeAmount,
                        1,
                        sampleStorageDifferential.toReference(),
                        leaf.username,
                        "charge of storage",
                        UUID.randomUUID().toString()
                    )

                    Accounting.charge.call(bulkRequestOf(request1), serviceClient).orThrow()

                    val request2 = ChargeWalletRequestItem(
                        leaf.owner,
                        input.chargeAmount*2,
                        1,
                        sampleStorageDifferential.toReference(),
                        leaf.username,
                        "charge of storage",
                        UUID.randomUUID().toString()
                    )

                    Accounting.charge.call(bulkRequestOf(request2), serviceClient).orThrow()

                    if (input.extraAllocateAndCharge) {
                        Accounting.deposit.call(
                            bulkRequestOf(
                                DepositToWalletRequestItem(
                                    leaf.owner,
                                    findWallet(subproject.client, sampleStorageDifferential.category)?.allocations?.first()?.id!!,
                                    1000L,
                                    "new deposit",
                                    transactionId = UUID.randomUUID().toString()
                                )
                            ),
                            subproject.client
                        )

                        val request3 = ChargeWalletRequestItem(
                            leaf.owner,
                            input.chargeAmount*2,
                            1,
                            sampleStorageDifferential.toReference(),
                            leaf.username,
                            "charge of storage",
                            UUID.randomUUID().toString()
                        )

                        Accounting.charge.call(bulkRequestOf(request3), serviceClient).orThrow()
                    }

                    Out(
                        leaves.map {
                            findWallet(it.client, sampleStorageDifferential.category)!!
                        }
                    )
                }

                case("charge twice") {
                    input(
                        In(
                            rootBalance = 10000L,
                            chainFromRoot = listOf(Allocation(true, 3000L), Allocation(true, 1000L)),
                            chargeAmount = 100L
                        )
                    )

                    check {
                        assertEquals(800, output.wallets.last().allocations.first().balance)
                    }
                }

                case("charge twice - result in overcharge") {
                    input(
                        In(
                            rootBalance = 10000L,
                            chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                            chargeAmount = 750L
                        )
                    )

                    check {
                        assertEquals(-500, output.wallets.last().allocations.first().balance)
                    }
                }

                case("charge twice - result in overcharge - new alloc and charge again") {
                    input(
                        In(
                            rootBalance = 10000L,
                            chainFromRoot = listOf(Allocation(true, 1000L), Allocation(true, 1000L)),
                            chargeAmount = 750L,
                            extraAllocateAndCharge = true
                        )
                    )

                    check {
                        assertEquals(0, output.wallets.last().allocations.first().balance)
                        assertEquals(500, output.wallets.last().allocations.last().balance)
                    }
                }
            }
        }
    }
}
