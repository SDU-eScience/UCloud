package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.grant.api.GrantsV2
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.collections.ArrayList
import kotlin.test.assertEquals

class AccountingCorrectnessComputeTest : IntegrationTest() {
    override fun defineTests() {
        run {
            class In(
                val numberOfJobs: Int,
                val durationPerJob: Long,
                val numberOfExtraDeposits: Long? = null,
                val extraProjectLayer: Boolean = false
            )

            class Out(
                val initialWallets: List<WalletV2>,
                val postWallets: List<WalletV2>,
                val postRootWallets: List<WalletV2>
            )

            test<In, Out>("test correctness of charges") {
                execute {
                    val providerInfo = createSampleProducts()
                    val root = initializeRootProject(providerInfo.projectId)
                    var createdProject = initializeNormalProject(root, initializeWallet = true, amount = 1_000_000_000)
                    if (input.extraProjectLayer) {
                        createdProject =
                            initializeNormalProject(createdProject, initializeWallet = true, amount = 1_000_000)
                    }

                    val createdProjectWalletOwner = WalletOwner.Project(createdProject.projectId)

                    if (input.numberOfExtraDeposits != null) {
                        for (i in 1..input.numberOfExtraDeposits) {

                            GrantsV2.submitRevision.call(
                                GrantsV2.SubmitRevision.Request(
                                    revision = GrantApplication.Document(
                                        GrantApplication.Recipient.ExistingProject(createdProject.projectId),
                                        listOf(
                                            GrantApplication.AllocationRequest(
                                                sampleCompute.category.name,
                                                UCLOUD_PROVIDER,
                                                root.projectId,
                                                1000000,
                                                GrantApplication.Period(
                                                    Time.now(),
                                                    Time.now() + 10000000
                                                )
                                            )
                                        ),
                                        GrantApplication.Form.GrantGiverInitiated("Gifted automatically", false),
                                        parentProjectId = root.projectId,
                                        revisionComment = "Gifted automatically"
                                    ),
                                    comment = "Gift"
                                ),
                                adminClient
                            )
                        }
                    }

                    val initialWallets =
                        findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleCompute.category }

                    val bulk = ArrayList<UsageReportItem>()
                    for (job in 0 until input.numberOfJobs) {
                        bulk.add(
                            UsageReportItem(
                                true,
                                createdProjectWalletOwner,
                                ProductCategoryIdV2(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider
                                ),
                                input.durationPerJob * sampleCompute.price,
                                ChargeDescription(
                                    null,
                                    null
                                )
                            )
                        )
                    }
                    bulk.chunked(1000).forEach { charges ->
                        runBlocking {
                            AccountingV2.reportUsage.call(
                                BulkRequest(
                                    charges
                                ),
                                serviceClient
                            ).orThrow()
                        }
                    }

                    delay(500)

                    val postRootWallets =
                        findWalletsInternal(WalletOwner.Project(root.projectId)).filter { it.paysFor == sampleCompute.category }
                    val wallets =
                        findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleCompute.category }

                    Out(
                        initialWallets = initialWallets,
                        postWallets = wallets,
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
                    check {
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                    }

                    case("10 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("100 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("1000 charges, 10 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("10000 charges, 1 minutes in Project") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 1L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("1 charge, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 1,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("10 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("100 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 100,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("1000 charges, 10 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 1000,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }

                    case("10000 charges, 2 minutes Root check") {
                        input(
                            In(
                                numberOfJobs = 10000,
                                durationPerJob = 2L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(initialState.initialQuota, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")

                            assertEquals(totalCharge, postState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postState.initialQuota, "quota wrong")

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
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price


                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

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
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price


                            assertEquals(initialState.initialQuota, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")

                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        }
                    }

                    case("10 charges, 10 minutes in low layer project") {
                        input(
                            In(
                                numberOfJobs = 10,
                                durationPerJob = 10L
                            )
                        )
                        check {
                            val initialState = getSumOfWallets(output.initialWallets)
                            val postChargeState = getSumOfWallets(output.postWallets)
                            val postChargeRootState = getSumOfWallets(output.postRootWallets)
                            val totalCharge = input.durationPerJob * input.numberOfJobs * sampleCompute.price

                            assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                            assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                            assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")

                        }
                    }
                }
            }
        }
    }
}
