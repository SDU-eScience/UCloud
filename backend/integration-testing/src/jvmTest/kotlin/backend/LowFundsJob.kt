package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.serviceJobs.LowFundsJob
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.checkNumberInRange
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.*
import kotlin.test.assertEquals

class LowFundsJobTest : IntegrationTest() {
    override fun defineTests() {

        run {
            class In(
                val config: Configuration,
                val numberOfChecks: Long,
                val computeCredits: Long,
                val storageCredits: Long,
                val networkUnits: Long,
                val projectCredits1: Long,
                val projectCredits2: Long,
                val refillAmount: Long = 10000.DKK,
                val checkAfterRefill: Boolean = false,
                val refillWithRootDeposit: Boolean = false
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
                                input.computeCredits,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleStorage.category,
                                WalletOwner.User(user2.username),
                                input.storageCredits,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleNetworkIp.category,
                                WalletOwner.User(user3.username),
                                input.networkUnits,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
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
                                input.projectCredits1,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
                            )
                        ),
                        serviceClient
                    ).orThrow()
                    Accounting.rootDeposit.call(
                        bulkRequestOf(
                            RootDepositRequestItem(
                                sampleCompute.category,
                                WalletOwner.Project(projectId),
                                input.projectCredits2,
                                "Initial deposit",
                                transactionId = UUID.randomUUID().toString()
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

                    val isNotified = if (!input.checkAfterRefill) {
                        UCloudLauncher.db.withSession { session ->
                            session.sendPreparedStatement(
                                """
                                select * from accounting.wallets
                            """
                            ).rows.map { it.getBoolean("low_funds_notifications_send")!! }
                        }
                    } else {
                        if (input.refillWithRootDeposit) {
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        WalletOwner.Project(projectId),
                                        input.refillAmount,
                                        "Initial deposit",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        } else {
                            val sourceUser = createUser("sourceUser")

                            val root = Projects.create.call(
                                CreateProjectRequest(
                                    "root",
                                    principalInvestigator = sourceUser.username
                                ),
                                serviceClient
                            ).orThrow().id

                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        WalletOwner.Project(root),
                                        100000000.DKK,
                                        "Initial deposit",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                ),
                                serviceClient
                            ).orThrow()

                            val allocationId = Wallets.browse.call(
                                WalletBrowseRequest(),
                                sourceUser.client.withProject(root)
                            ).orThrow().items.first().allocations.first().id

                            Accounting.deposit.call(
                                bulkRequestOf(
                                    DepositToWalletRequestItem(
                                        WalletOwner.Project(projectId),
                                        allocationId,
                                        input.refillAmount,
                                        "deposit",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                ),
                                sourceUser.client
                            ).orThrow()
                        }

                        UCloudLauncher.db.withSession { session ->
                            session.sendPreparedStatement(
                                """
                                select * from accounting.wallets
                            """
                            ).rows
                            .map { it.getBoolean("low_funds_notifications_send")!! }
                        }
                    }

                    Out(isNotified)
                }
                case ("simple check of funds") {
                    input(
                        In(
                            Configuration(
                                computeCreditsNotificationLimit = 100000.DKK,
                                computeUnitsNotificationLimit = 100000,
                                storageCreditsNotificationLimit = 100000.DKK,
                                storageUnitsNotificationLimitInGB = 1000,
                                storageQuotaNotificationLimitInGB = 50L
                            ),
                            numberOfChecks = 1,
                            computeCredits = 10000.DKK,
                            storageCredits = 100000.DKK,
                            networkUnits = 10L,
                            projectCredits1 = 10000.DKK,
                            projectCredits2 = 10000.DKK
                        )
                    )
                    check {
                        assertEquals(4, output.notificationSend.size)
                        assertEquals(2, output.notificationSend.filter { it }.size)
                    }
                }

                case ("Check that number of notifications stays the same as before even on multiple runs") {
                    input(
                        In(
                            Configuration(
                                computeCreditsNotificationLimit = 100000.DKK,
                                computeUnitsNotificationLimit = 100000,
                                storageCreditsNotificationLimit = 100000.DKK,
                                storageUnitsNotificationLimitInGB = 1000,
                                storageQuotaNotificationLimitInGB = 50L
                            ),
                            numberOfChecks = 5,
                            computeCredits = 10000.DKK,
                            storageCredits = 100000.DKK,
                            networkUnits = 10L,
                            projectCredits1 = 10000.DKK,
                            projectCredits2 = 10000.DKK
                        )
                    )
                    check {
                        assertEquals(4, output.notificationSend.size)
                        assertEquals(2, output.notificationSend.filter { it }.size)
                    }
                }

                case ("check rest of notification after refill use root deposit") {
                    input(
                        In(
                            Configuration(
                                computeCreditsNotificationLimit = 25000.DKK,
                                computeUnitsNotificationLimit = 100000,
                                storageCreditsNotificationLimit = 100000.DKK,
                                storageUnitsNotificationLimitInGB = 1000,
                                storageQuotaNotificationLimitInGB = 50L
                            ),
                            numberOfChecks = 5,
                            computeCredits = 10000.DKK,
                            storageCredits = 100000.DKK,
                            networkUnits = 10L,
                            projectCredits1 = 10000.DKK,
                            projectCredits2 = 10000.DKK,
                            checkAfterRefill = true,
                            refillWithRootDeposit = true
                        )
                    )
                    check {
                        assertEquals(4, output.notificationSend.size)
                        assertEquals(1, output.notificationSend.filter { it }.size)
                    }
                }

                case ("check rest of notification after refill use deposit") {
                    input(
                        In(
                            Configuration(
                                computeCreditsNotificationLimit = 25000.DKK,
                                computeUnitsNotificationLimit = 100000,
                                storageCreditsNotificationLimit = 100000.DKK,
                                storageUnitsNotificationLimitInGB = 1000,
                                storageQuotaNotificationLimitInGB = 50L
                            ),
                            numberOfChecks = 5,
                            computeCredits = 10000.DKK,
                            storageCredits = 100000.DKK,
                            networkUnits = 10L,
                            projectCredits1 = 10000.DKK,
                            projectCredits2 = 10000.DKK,
                            checkAfterRefill = true,
                            refillWithRootDeposit = false
                        )
                    )
                    check {
                        assertEquals(5, output.notificationSend.size)
                        assertEquals(1, output.notificationSend.filter { it }.size)
                    }
                }

            }
        }


    }
}
