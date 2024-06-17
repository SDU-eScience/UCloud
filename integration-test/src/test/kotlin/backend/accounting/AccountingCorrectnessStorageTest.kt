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

class AccountingCorrectnessStorageTest : IntegrationTest() {
    override fun defineTests() {
        run {
            class In(
                val GBUsed: Long,
                val numberOfExtraDeposits: Int? = null,
                val reduceUsageTo: Long? = null
            )

            class Out(
                val initialWallets: List<WalletV2>,
                val postWallets: List<WalletV2>,
                val postRootWallets: List<WalletV2>
            )

            test<In, Out>("test correctness of charges storage"){
                execute {
                    val providerInfo = createSampleProducts()
                    val root = initializeRootProject(providerInfo.projectId)
                    val createdProject = initializeNormalProject(root, amount = 1_000_000_000, product = sampleStorageDifferential)
                    val createdProjectWalletOwner = WalletOwner.Project(createdProject.projectId)

                    if (input.numberOfExtraDeposits != null) {
                        for (i in 1..input.numberOfExtraDeposits) {
                            GrantsV2.submitRevision.call(
                                GrantsV2.SubmitRevision.Request(
                                    revision = GrantApplication.Document(
                                        GrantApplication.Recipient.ExistingProject(createdProject.projectId),
                                        listOf(
                                            GrantApplication.AllocationRequest(
                                                sampleStorageDifferential.category.name,
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

                    val initialWallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleStorageDifferential.category  }

                    val bulk = ArrayList<UsageReportItem>()
                    bulk.add(
                        UsageReportItem(
                            isDeltaCharge = false,
                            createdProjectWalletOwner,
                            ProductCategoryIdV2(
                                sampleStorageDifferential.category.name,
                                sampleStorageDifferential.category.provider
                            ),
                            input.GBUsed,
                            ChargeDescription(
                                null,
                                null
                            )
                        )
                    )

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

                    if (input.reduceUsageTo != null) {
                        val reduceBulk = ArrayList<UsageReportItem>()
                        reduceBulk.add(
                            UsageReportItem(
                                isDeltaCharge = false,
                                createdProjectWalletOwner,
                                ProductCategoryIdV2(
                                    sampleStorageDifferential.category.name,
                                    sampleStorageDifferential.category.provider
                                ),
                                input.reduceUsageTo,
                                ChargeDescription(
                                    null,
                                    null
                                )
                            )
                        )

                        reduceBulk.chunked(1000).forEach { charges ->
                            runBlocking {
                                AccountingV2.reportUsage.call(
                                    BulkRequest(
                                        charges
                                    ),
                                    serviceClient
                                ).orThrow()
                            }
                        }
                    }

                    val postRootWallets = findWalletsInternal(WalletOwner.Project(root.projectId)).filter { it.paysFor == sampleStorageDifferential.category  }
                    val wallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleStorageDifferential.category  }

                    Out(
                        initialWallets = initialWallets,
                        postWallets = wallets,
                        postRootWallets = postRootWallets
                    )
                }

                case("1 GB in Project") {
                    input(
                        In(
                            GBUsed = 1,
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")
                    }
                }

                case("10 GB in Project") {
                    input(
                        In(
                            GBUsed = 10,
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")
                    }
                }

                case("1000 GB in Project") {
                    input(
                        In(
                            GBUsed = 1000,
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")
                    }
                }

                case("1000 GB in Project, with extra deposits") {
                    input(
                        In(
                            GBUsed = 1000,
                            numberOfExtraDeposits = 3
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")
                    }
                }

                case("2_000_000_000 GB -  IS OVERCHARGE") {
                    input(
                        In(
                            GBUsed = 2_000_000_000
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(totalCharge, postChargeState.treeUsage, "Project tree Usage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "project Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeRootState.treeUsage, "root treeUsage wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root local wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                    }
                }

                case("Overcharge and reduce") {
                    input(
                        In(
                            GBUsed = 2_000_000_000,
                            reduceUsageTo = 5000
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.reduceUsageTo!!

                        assertEquals(totalCharge, postChargeState.treeUsage, "Project tree Usage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "project Usage wrong")
                        assertEquals(totalCharge, postChargeRootState.treeUsage, "root treeUsage wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root local wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                    }
                }

                case("10 GB in Project - reduce to 5 GB") {
                    input(
                        In(
                            GBUsed = 10,
                            reduceUsageTo = 5
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.reduceUsageTo!!

                        assertEquals(totalCharge, postChargeRootState.treeUsage, "treeUsage wrong")
                        assertEquals(totalCharge, postChargeState.localUsage, "Usage wrong")
                        assertEquals(initialState.initialQuota, postChargeState.initialQuota, "quota wrong")
                        assertEquals(0, postChargeRootState.localUsage, "Root Usage wrong")
                    }
                }
            }
        }
    }
}
