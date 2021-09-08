package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.accounting.api.Accounting
import dk.sdu.cloud.accounting.api.RootDepositRequestItem
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.services.serviceJobs.LowFundsJob
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.checkNumberInRange
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.db.async.withSession
import kotlin.test.assertEquals

class LowFundsJobTest : IntegrationTest() {
    override fun defineTests() {
        run {
            class In(
                val config: Configuration,
                val numberOfChecks: Long
            )
            class Out(
                val notificationSend: List<Boolean>
            )

            test<In, Out>("Low Funds Cron Job test") {
                execute {
                    createSampleProducts()
                    val user1 = createUser("user1")
                    val user2 = createUser("user2")
                    val user3 = createUser("user3")

                    //deposit to either user or project
                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.User(user1.username),
                                10000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.User(user2.username),
                                1000000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.User(user3.username),
                                1000000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    val projectId = Projects.create.call(
                        CreateProjectRequest(
                            "Project",
                            principalInvestigator = user1.username
                        ),
                        serviceClient
                    ).orThrow().id

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.Project(projectId),
                                1000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()
                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.Project(projectId),
                                1000.DKK,
                                "Initial deposit"
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    for ( i in 0..input.numberOfChecks) {
                        LowFundsJob(
                            db = UCloudLauncher.db,
                            serviceClient = serviceClient,
                            config = input.config
                        ).checkWallets()
                    }

                    val isNotified = UCloudLauncher.db.withSession { session ->
                        session.sendPreparedStatement(
                            """
                                select * from accounting.wallets
                            """
                        ).rows.map { it.getBoolean("low_funds_notifications_send")!! }
                    }

                    Out(isNotified)
                }
                case ("simple check of funds") {
                    input(
                        In(
                            Configuration(
                                100000.DKK,
                                100000.DKK,
                                100000.DKK,
                                100000.DKK,
                                100000.DKK
                            ),
                            numberOfChecks = 1
                        )
                    )
                    check {
                        assertEquals(4, output.notificationSend.size)
                        assertEquals(2, output.notificationSend.filter { it }.size)
                    }
                }

                case ("Check that number of notifications stays the same as before") {
                    input(
                        In(
                            Configuration(
                                100000.DKK,
                                100000.DKK,
                                100000.DKK,
                                100000.DKK,
                                100000.DKK
                            ),
                            numberOfChecks = 5
                        )
                    )
                    check {
                        assertEquals(4, output.notificationSend.size)
                        assertEquals(2, output.notificationSend.filter { it }.size)
                    }
                }
            }
        }


    }
}
