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
import kotlin.test.assertTrue

class AccountingCorrectnessTest : IntegrationTest() {


        override fun defineTests() {
            run {
                class In(
                    val numberOfJobs: Int,
                    val durationPerJob: Long
                )

                class Out(
                    val initialWallets: Set<Wallet>,
                    val wallets: Set<Wallet>
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

                        val wallets = findWalletsInternal(createdProjectWalletOwner)

                        println(initialwallets)
                        println(wallets)
                        Out(
                            initialWallets = initialwallets,
                            wallets = wallets
                        )
                    }

                    case("1 charge, 10 hours") {
                        input(In(1, 10L))
                        check{
                            val allocation = output.initialWallets.filter { it.paysFor == sampleCompute.category }
                        }
                    }
                }
            }
        }
}
