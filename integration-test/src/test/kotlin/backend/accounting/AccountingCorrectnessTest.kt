package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.backend.compute.toReference
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.integration.utils.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.test.assertEquals

class AccountingCorrectnessTest : IntegrationTest() {

        data class TotalWalletContent(
            val initialBalance: Long,
            val localBalance: Long,
            val currentBalance: Long
        )

        fun getSumOfWallets(wallets: List<Wallet>): TotalWalletContent{
            val initialBalance = wallets.sumOf { wallet -> wallet.allocations.sumOf { it.initialBalance } }
            val localBalance = wallets.sumOf { wallet -> wallet.allocations.sumOf { it.localBalance } }
            val currentBalance = wallets.sumOf { wallet -> wallet.allocations.sumOf { it.balance } }
            return TotalWalletContent(initialBalance, localBalance, currentBalance)
        }
        override fun defineTests() {
            run {
                class In(
                    val numberOfJobs: Int,
                    val durationPerJob: Long,
                    val numberOfExtraDeposits: Long? = null
                )

                class Out(
                    val initialWallets: List<Wallet>,
                    val postWallets: List<Wallet>,
                    val initialRootWallets: List<Wallet>,
                    val postRootWallets: List<Wallet>
                )

                test<In, Out>("test correctness of charges"){
                    execute {
                        val normalUser = createUser(
                            "user-${UUID.randomUUID()}"
                        )
                        createSampleProducts()
                        val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                        val createdProject = initializeNormalProject(root)
                        val createdProjectWalletOwner = WalletOwner.Project(createdProject.projectId)
                        val initialRootWallets = findWalletsInternal(WalletOwner.Project(root)).filter { it.paysFor == sampleCompute.category  }

                        if (input.numberOfExtraDeposits != null) {
                            for (i in 1..input.numberOfExtraDeposits) {
                                Accounting.deposit.call(
                                    bulkRequestOf(
                                        DepositToWalletRequestItem(
                                            createdProjectWalletOwner,
                                            initialRootWallets.first().allocations.first().id,
                                            10000,
                                            "another deposit",
                                            isProject = true
                                        )
                                    ),
                                    adminClient
                                )
                            }
                        }

                        val initialWallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleCompute.category  }

                        val bulk = ArrayList<ChargeWalletRequestItem>()
                        for (job in 0 until input.numberOfJobs) {
                            bulk.add(
                                ChargeWalletRequestItem(
                                    payer = WalletOwner.Project(createdProject.projectId),
                                    units = input.durationPerJob,
                                    periods = 1,
                                    product = sampleCompute.toReference(),
                                    performedBy = createdProject.piUsername,
                                    description = "A job is being charged: $job"
                                )
                            )
                        }
                        bulk.chunked(1000).forEach { charges ->
                            Accounting.charge.call(
                                BulkRequest(charges),
                                serviceClient
                            ).orThrow()
                        }
                        val postRootWallets = findWalletsInternal(WalletOwner.Project(root)).filter { it.paysFor == sampleCompute.category  }
                        val wallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleCompute.category  }

                        Out(
                            initialWallets = initialWallets,
                            postWallets = wallets,
                            initialRootWallets = initialRootWallets,
                            postRootWallets = postRootWallets
                        )
                    }

                    case("1 charge, 10 hours in Project") {
                        input(
                            In(
                                numberOfJobs = 1,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10 charge, 10 hours in Project") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("100 charge, 10 hours in Project") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("1000 charge, 10 hours in Project") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10000 charge, 2 hours in Project") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 2L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("1 charge, 10 hours Root check") {
                        input(
                            In(
                                numberOfJobs = 1,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialRootWallets)
                            val postChargeState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10 charge, 10 hours Root check") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialRootWallets)
                            val postChargeState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("100 charge, 10 hours Root check") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialRootWallets)
                            val postChargeState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("1000 charge, 10 hours Root check") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialRootWallets)
                            val postChargeState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10000 charge, 2 hours Root check") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 2L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialRootWallets)
                            val postChargeState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10000 charge, 2 hours, multiple allocations") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 2L,
                                numberOfExtraDeposits = 3L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10000 charge, 2 hours - IS OVERCHARGE") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 50L,
                                numberOfExtraDeposits = 3L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)

                            val chargeLimit = initialState.initialBalance % (1000 * input.durationPerJob * sampleCompute.pricePerUnit)

                            assertEquals(chargeLimit, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(chargeLimit, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }
                }
            }
        }
}
