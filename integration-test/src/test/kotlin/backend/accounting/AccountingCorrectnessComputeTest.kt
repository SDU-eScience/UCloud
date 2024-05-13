package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.service.Time
import kotlin.collections.ArrayList
import kotlin.test.assertEquals

/*class AccountingCorrectnessComputeTest : IntegrationTest() {
        override fun defineTests() {
            run {
                class In(
                    val numberOfJobs: Int,
                    val durationPerJob: Long,
                    val numberOfExtraDeposits: Long? = null,
                    val extraProjectLayer: Boolean = false
                )

                class Out(
                    val initialWallets: List<Wallet>,
                    val postWallets: List<Wallet>,
                    val initialRootWallets: List<Wallet>,
                    val postRootWallets: List<Wallet>
                )

                test<In, Out>("test correctness of charges"){
                    execute {
                        createSampleProducts()
                        val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                        var createdProject = initializeNormalProject(root, amount = 1_000_000_000)
                        if (input.extraProjectLayer) {
                            createdProject = initializeNormalProject(createdProject.projectId, amount = 1_000_000)
                        }
                        val createdProjectWalletOwner = WalletOwner.Project(createdProject.projectId)
                        val initialRootWallets = findWalletsInternal(WalletOwner.Project(root)).filter { it.paysFor == sampleCompute.category  }

                        if (input.numberOfExtraDeposits != null) {
                            for (i in 1..input.numberOfExtraDeposits) {
                                Accounting.deposit.call(
                                    bulkRequestOf(
                                        DepositToWalletRequestItem(
                                            createdProjectWalletOwner,
                                            initialRootWallets.first().allocations.first().id,
                                            1000000,
                                            "another deposit",
                                            grantedIn = null,
                                            startDate = Time.now(),
                                            endDate = Time.now() + 10000000
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

                    case("1 charge, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 1,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("10 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("100 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("1000 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("10000 charges, 1 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 1L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("1 charge, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 1,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialRootState.localBalance, postChargeRootState.localBalance, "Usage wrong")
                            assertEquals(initialRootState.initialBalance, postChargeRootState.initialBalance, "quota wrong")
                        }
                    }

                    case("10 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialRootState.localBalance, postChargeRootState.localBalance, "Usage wrong")
                            assertEquals(initialRootState.initialBalance, postChargeRootState.initialBalance, "quota wrong")
                        }
                    }

                    case("100 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialRootState.localBalance, postChargeRootState.localBalance, "Usage wrong")
                            assertEquals(initialRootState.initialBalance, postChargeRootState.initialBalance, "quota wrong")
                        }
                    }

                    case("1000 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialRootState.localBalance, postChargeRootState.localBalance, "Usage wrong")
                            assertEquals(initialRootState.initialBalance, postChargeRootState.initialBalance, "quota wrong")
                        }
                    }

                    case("10000 charges, 2 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 2L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - initialState.initialBalance, postChargeRootState.currentBalance, "root treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postState.localBalance, "Project local usage wrong" )
                            assertEquals(initialRootState.localBalance, postChargeRootState.localBalance, "root Usage wrong")
                            assertEquals(initialRootState.initialBalance, postChargeRootState.initialBalance, "root quota wrong")
                        }
                    }

                    case("10000 charges, 1 minut, multiple allocations") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 1L,
                                numberOfExtraDeposits = 3L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit


                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance, initialState.initialBalance, "usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }

                    case("1000 charges, 20 minuts - IS OVERCHARGE") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 20L,
                                numberOfExtraDeposits = 3L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(0, postChargeState.currentBalance, "project treeusage wrong")
                            assertEquals(initialState.initialBalance - totalCharge, postChargeState.localBalance, "project Local usage wrong")
                            assertEquals(initialRootState.initialBalance - postChargeState.initialBalance, postChargeRootState.currentBalance, "root treeUsage wrong")
                            assertEquals(initialState.initialBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
                        }
                    }

                    case("10 charges, 10 minutes in low layer project") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check{
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val initialRootState = getSumOfWallets(output.initialRootWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.pricePerUnit

                            assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                            assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                            assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        }
                    }
                }
            }
        }
}
*/