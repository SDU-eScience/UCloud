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
import dk.sdu.cloud.service.Time
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
                val initialWallets: List<Wallet>,
                val postWallets: List<Wallet>,
                val initialRootWallets: List<Wallet>,
                val postRootWallets: List<Wallet>
            )

            test<In, Out>("test correctness of charges storage"){
                execute {
                    createSampleProducts()
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val createdProject = initializeNormalProject(root, amount = 1_000_000_000)
                    val createdProjectWalletOwner = WalletOwner.Project(createdProject.projectId)
                    val initialRootWallets = findWalletsInternal(WalletOwner.Project(root)).filter { it.paysFor == sampleStorageDifferential.category  }

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

                    val initialWallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleStorageDifferential.category  }

                    val bulk = ArrayList<ChargeWalletRequestItem>()
                    bulk.add(
                        ChargeWalletRequestItem(
                            payer = WalletOwner.Project(createdProject.projectId),
                            units = input.GBUsed,
                            periods = 1,
                            product = sampleStorageDifferential.toReference(),
                            performedBy = createdProject.piUsername,
                            description = "Charging usage of Storage"
                        )
                    )

                    Accounting.charge.call(
                        BulkRequest(bulk),
                        serviceClient
                    ).orThrow()

                    if (input.reduceUsageTo != null) {
                        val bulk = ArrayList<ChargeWalletRequestItem>()
                        bulk.add(
                            ChargeWalletRequestItem(
                                payer = WalletOwner.Project(createdProject.projectId),
                                units = input.reduceUsageTo,
                                periods = 1,
                                product = sampleStorageDifferential.toReference(),
                                performedBy = createdProject.piUsername,
                                description = "Charging usage of Storage"
                            )
                        )

                        Accounting.charge.call(
                            BulkRequest(bulk),
                            serviceClient
                        ).orThrow()
                    }

                    val postRootWallets = findWalletsInternal(WalletOwner.Project(root)).filter { it.paysFor == sampleStorageDifferential.category  }
                    val wallets = findWalletsInternal(createdProjectWalletOwner).filter { it.paysFor == sampleStorageDifferential.category  }

                    Out(
                        initialWallets = initialWallets,
                        postWallets = wallets,
                        initialRootWallets = initialRootWallets,
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                        assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "Root Usage wrong")
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                        assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "Root Usage wrong")
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                        assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "Root Usage wrong")
                    }
                }

                case("1000 GB in Project") {
                    input(
                        In(
                            GBUsed = 1000,
                            numberOfExtraDeposits = 3
                        )
                    )
                    check{
                        val initialState = getSumOfWallets(output.initialWallets)
                        val postChargeState = getSumOfWallets(output.postWallets)
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                        assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "Root Usage wrong")
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.GBUsed

                        assertEquals(initialRootState.initialBalance - initialState.initialBalance, postChargeRootState.currentBalance, "project treeusage wrong")
                        assertEquals(initialState.initialBalance - totalCharge, postChargeState.localBalance, "project Local usage wrong")
                        assertEquals(initialRootState.initialBalance - postChargeState.initialBalance, postChargeRootState.currentBalance, "root treeUsage wrong")
                        assertEquals(initialState.initialBalance - totalCharge, postChargeState.localBalance, "localBalance wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.reduceUsageTo!!

                        println(initialState)
                        println(postChargeState)
                        println(initialRootState)
                        println(postChargeRootState)


                        assertEquals(initialState.currentBalance - totalCharge, postChargeState.currentBalance, "project treeusage wrong")
                        assertEquals(initialState.initialBalance - totalCharge, postChargeState.localBalance, "project Local usage wrong")
                        assertEquals(initialRootState.initialBalance - totalCharge, postChargeRootState.currentBalance, "root treeUsage wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "root localBalance wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "initialBalance wrong")
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
                        val initialRootState = getSumOfWallets(output.initialRootWallets)
                        val postChargeRootState = getSumOfWallets(output.postRootWallets)
                        val totalCharge = input.reduceUsageTo!!

                        assertEquals(initialRootState.currentBalance - totalCharge, postChargeRootState.currentBalance, "treeUsage wrong")
                        assertEquals(initialState.localBalance - totalCharge, postChargeState.localBalance, "Usage wrong")
                        assertEquals(initialState.initialBalance, postChargeState.initialBalance, "quota wrong")
                        assertEquals(initialRootState.initialBalance, postChargeRootState.localBalance, "Root Usage wrong")
                    }
                }
            }
        }
    }
}

