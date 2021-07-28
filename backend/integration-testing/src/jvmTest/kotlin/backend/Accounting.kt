package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
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
import kotlin.test.assertEquals

suspend fun addFundsToPersonalProject(
    rootProject: String,
    username: String,
    product: ProductCategoryId = sampleCompute.category,
    amount: Long = 10_000.DKK
) {
    Accounting.transfer.call(
        bulkRequestOf(
            TransferToWalletRequestItem(product, WalletOwner.User(username), amount)
        ),
        serviceClient.withProject(rootProject)
    ).orThrow()
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
                val walletBelongsToProject: Boolean,
                val initialBalance: Long,
                val units: Long,
                val numberOfProducts: Long = 1,
                val product: Product = sampleCompute
            )

            class Out(
                val newBalance: Long
            )

            test<In, Out>("Deposit and charge") {
                execute {
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
                        client = serviceClient.withProject(id)
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
                                it.endDate == null &&
                                it.associatedWith == null
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

                    case("$name with enough credits") {
                        input(In(isProject, 1000.DKK, 10.DKK))
                        check {
                            assertEquals(990.DKK, output.newBalance)
                        }
                    }

                    case("$name with over-charge") {
                        input(In(isProject, 10.DKK, 20.DKK))
                        check {
                            assertEquals(0.DKK, output.newBalance)
                        }
                    }
                }
            }
        }
    }

    /*
    @Test
    fun `test simple case accounting`() = t {
        createSampleProducts()
        val root = initializeRootProject(initializeWallet = false)

        // Create products
        // Initialize a root project
        // Set the balance at the root
        // Retrieve current balance
        // Perform a charge
        // Check that the balance has been updated
    }

    @Test
    fun `transfer to personal project`() = t {
        val initialBalance = 1000.DKK
        val transferAmount = 600.DKK
        val product = sampleCompute.category

        // Check initial balance in personal workspace
        // Attempt to transfer balance from the root project
        // Check that the balance has been transferred correctly
    }

    data class TestInput(...)
    data class TestOutput(...)

    test {
        prepare {
            ...
        }

        execute {
            TestOutput(...)
        }

        case("Test something") {
            input(TestInput(...))

            expectFailure {
                it is RPCException
            }

            check {
                output.fie == "dog"
            }
        }
    }
     */

    /*
    @Test
    fun `test charging a reservation of too large size`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        try {
            reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), initialBalance + 1)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(ex.httpStatusCode, HttpStatusCode.PaymentRequired)
        }
    }

    @Test
    fun `test reserving with negative amount`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        try {
            reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), -1L)
            assert(false)
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "is a client error") { it.value in 400..499 }
        }
    }

    @Test
    fun `test charging negative amount`() = t {
        val initialBalance = 1000.DKK
        val root = initializeRootProject(amount = initialBalance)
        val id = reserveCredits(Wallet(root, WalletOwnerType.PROJECT, sampleCompute.category), 50.DKK)
        try {
            Wallets.chargeReservation.call(
                ChargeReservationRequest(id, -1L, 0L),
                serviceClient
            ).orThrow()
        } catch (ex: RPCException) {
            assertThatInstance(ex.httpStatusCode, "fails") { it.value in 400..499 }
        }
    }

    @Test
    fun `test that parents are all charged`() = t {
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

        val id = reserveCredits(Wallet(parents.last(), WalletOwnerType.PROJECT, category), charge)
        Wallets.chargeReservation.call(
            ChargeReservationRequest(id, charge, 1),
            serviceClient
        ).orThrow()

        for (p in parents) {
            assertThatInstance(
                findProjectWallet(p, r.piClient, category),
                "has been charged"
            ) { it != null && it.balance == initialBalance - charge }
        }
    }

    @Test
    fun `test charging a personal project`() = t {
        val initialBalance = 1000.DKK
        val transferAmount = 600.DKK
        val chargeAmount = 200.DKK
        val product = sampleCompute.category

        val root = initializeRootProject(amount = initialBalance)
        val user = createUser()
        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "is empty"
        ) { it == null || it.balance == 0L }

        val transferRequest = TransferToPersonalRequest(
            listOf(
                SingleTransferRequest(
                    "_UCloud",
                    transferAmount,
                    Wallet(root, WalletOwnerType.PROJECT, product),
                    Wallet(user.username, WalletOwnerType.USER, product)
                )
            )
        )

        Wallets.transferToPersonal.call(transferRequest, serviceClient).orThrow()

        val id = reserveCredits(Wallet(user.username, WalletOwnerType.USER, product), chargeAmount)
        Wallets.chargeReservation.call(ChargeReservationRequest(id, chargeAmount, 1), serviceClient).orThrow()

        assertThatInstance(
            findPersonalWallet(user.username, user.client, product),
            "has been charged"
        ) { it != null && it.balance == transferAmount - chargeAmount }
    }

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
