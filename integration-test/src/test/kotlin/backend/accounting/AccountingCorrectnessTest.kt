package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.integration.IntegrationTest
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
                    val durationPerJob: Long
                )

                class Out(
                    val initialWallets: Set<Wallet>,
                    val postWallets: Set<Wallet>,
                    val initialRootWallets: Set<Wallet>,
                    val postRootWallets: Set<Wallet>
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
                        val initialRootWallets = findWalletsInternal(WalletOwner.Project(root))
                        val initialwallets = findWalletsInternal(createdProjectWalletOwner)

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
                            )
                        }
                        val postRootWallets = findWalletsInternal(WalletOwner.Project(root))
                        val wallets = findWalletsInternal(createdProjectWalletOwner)

                        Out(
                            initialWallets = initialwallets,
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
                            val initialState = getSumOfWallets(output.initialWallets.toList())
                            val postChargeState = getSumOfWallets(output.postWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialWallets.toList())
                            val postChargeState = getSumOfWallets(output.postWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialWallets.toList())
                            val postChargeState = getSumOfWallets(output.postWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialWallets.toList())
                            val postChargeState = getSumOfWallets(output.postWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialWallets.toList())
                            val postChargeState = getSumOfWallets(output.postWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialRootWallets.toList())
                            val postChargeState = getSumOfWallets(output.postRootWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialRootWallets.toList())
                            val postChargeState = getSumOfWallets(output.postRootWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialRootWallets.toList())
                            val postChargeState = getSumOfWallets(output.postRootWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialRootWallets.toList())
                            val postChargeState = getSumOfWallets(output.postRootWallets.toList())
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
                            val initialState = getSumOfWallets(output.initialRootWallets.toList())
                            val postChargeState = getSumOfWallets(output.postRootWallets.toList())
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit
                            println(totalCharge)
                            assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "currentBalance wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }
                }
            }
        }
}
