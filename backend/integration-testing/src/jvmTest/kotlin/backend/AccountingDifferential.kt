package dk.sdu.cloud.integration.backend

class AccountingTest : IntegrationTest() {
    override fun defineTests() {

        run {
            class In(
                val useProjectChain: Boolean,
                val transactionId: String,
                val useSingleProject: Boolean,
                val singleProjectMultipleAllocations: Boolean = false,
                val duplicateCharge: Boolean = false
            )

            class Out(
                val transactions: List<Transaction>,
                val firstChargeResults: List<Boolean>,
                val secondChargeResults: List<Boolean>
            )
            test<In, Out>("Transaction tests") {
                execute {
                    var firstChargeResults = emptyList<Boolean>()
                    var secondChargeResults = emptyList<Boolean>()

                    createSampleProducts()

                    if (input.useProjectChain) {
                        val projectAllocations = prepareProjectChain(
                            10000.DKK,
                            listOf(
                                Allocation(true, 5000.DKK),
                                Allocation(true, 2000.DKK),
                                Allocation(true, 1000.DKK)
                            ),
                            sampleCompute.category
                        )

                        val walletOwner = projectAllocations.last().owner
                        val user = projectAllocations.last().username
                        firstChargeResults = Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    walletOwner,
                                    5,
                                    1,
                                    sampleCompute.toReference(),
                                    user,
                                    "Charge for job",
                                    input.transactionId
                                )
                            ),
                            serviceClient
                        ).orThrow().responses

                        projectAllocations.last().client
                    }
                    if (input.useSingleProject) {
                        val user1 = createUser("user1")
                        val projectId = initializeRootProject(
                            user1.username,
                            true,
                            1000.DKK
                        )

                        val walletOwner = WalletOwner.Project(projectId)

                        if (input.singleProjectMultipleAllocations) {
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        walletOwner,
                                        1000.DKK,
                                        "deposit",
                                        transactionId = "extraDeposit"
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        }

                        firstChargeResults = Accounting.charge.call(
                            bulkRequestOf(
                                ChargeWalletRequestItem(
                                    walletOwner,
                                    if (input.singleProjectMultipleAllocations) 15000 else 5000,
                                    1,
                                    sampleCompute.toReference(),
                                    user1.username,
                                    "charging for job",
                                    input.transactionId
                                )
                            ),
                            serviceClient
                        ).orThrow().responses
                        if (input.duplicateCharge) {
                            secondChargeResults = Accounting.charge.call(
                                bulkRequestOf(
                                    ChargeWalletRequestItem(
                                        walletOwner,
                                        2000,
                                        1,
                                        sampleCompute.toReference(),
                                        user1.username,
                                        "I can do this again?",
                                        input.transactionId
                                    )
                                ),
                                serviceClient
                            ).orThrow().responses
                        }
                    }

                    val transactions = db.withSession { session ->
                        session.sendQuery(
                            """
                                select accounting.transaction_to_json(t, p, pc)
                                from
                                    accounting.transactions t join
                                    accounting.wallet_allocations alloc on t.affected_allocation_id = alloc.id join
                                    accounting.wallets w on alloc.associated_wallet = w.id join
                                    accounting.product_categories pc on w.category = pc.id join
                                    accounting.wallet_owner wo on w.owned_by = wo.id left join
                                    project.project_members pm on wo.project_id = pm.project_id left join
                                    accounting.products p on pc.id = p.category and t.product_id = p.id 
                            """
                        ).rows.map { defaultMapper.decodeFromString<Transaction>(it.getString(0)!!) }
                    }

                    Out(
                        transactions = transactions,
                        firstChargeResults = firstChargeResults,
                        secondChargeResults = secondChargeResults
                    )
                }
            }
        }
    }
}
