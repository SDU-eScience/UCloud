package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import kotlinx.coroutines.*
import kotlin.system.exitProcess
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountingStressTest: IntegrationTest() {
    override fun defineTests() {
        val timings = ArrayList<Pair<String, Long>>()
        run {
            class In(
                val numberOfCalls: Int,
                val project: Int,
                val productRef: ProductReference,
                val units: Long = 10,
                val periods: Long = 1,
                val depositcredits: Long = 10_000_000_000,
                val test: String = "Unknown"
            )

            class Out(
                val successes: List<Boolean>,
                val wallets: List<List<WalletAllocation>>
            )

            test<In, Out>("Stress test charge, sequential bulks of 1000 charges ") {
                execute {
                    createSampleProducts()
                    val productCategoryId = ProductCategoryId(input.productRef.category, input.productRef.provider)

                    val serviceClient = UCloudLauncher.serviceClient
                    val projectAllocations = prepareProjectChain(
                        input.depositcredits,
                        listOf(
                            Allocation(true, input.depositcredits/2),
                            Allocation(true, input.depositcredits/5),
                            Allocation(true, input.depositcredits/10)
                        ),
                        productCategoryId,
                        serviceClient = serviceClient
                    )

                    val root = projectAllocations.first()
                    val mid = projectAllocations[1]
                    val leaf = projectAllocations.last()

                    val project = when (input.project) {
                        1 -> root
                        2 -> mid
                        3 -> leaf
                        else -> {
                            throw IllegalArgumentException(
                                "Wrong argument for projectpicker. Should be 1-3 but ${input.project} was given"
                            )
                        }
                    }

                    val bulk = generateChargeBulk(
                        size = input.numberOfCalls,
                        walletOwner = project.owner,
                        units = input.units,
                        periods = input.periods,
                        productReference = input.productRef,
                        user = project.username
                    )

                    val requestsBulks = bulk.chunked(1000)
                    val responses = mutableListOf<Boolean>()
                    val start = System.currentTimeMillis()
                    requestsBulks.forEach { chargeRequests ->
                        responses.addAll(
                            Accounting.charge.call(
                                bulkRequestOf(chargeRequests),
                                serviceClient
                            ).orThrow().responses
                        )
                    }
                    val end = System.currentTimeMillis()
                    timings.add(Pair(input.test, (end-start)))

                    val results = mutableListOf<List<WalletAllocation>>()

                    val rootAllocations = retrieveAllocationsInternal(root.owner, productCategoryId, serviceClient)
                    val midAllocations = retrieveAllocationsInternal(mid.owner, productCategoryId, serviceClient)
                    val leafAllocations = retrieveAllocationsInternal(leaf.owner, productCategoryId, serviceClient)

                    results.add(rootAllocations)
                    results.add(midAllocations)
                    results.add(leafAllocations)

                    Out(
                        successes = responses,
                        wallets = results
                    )
                }

                 fun doCheck(
                     output: Out,
                     input: In,
                     allSucceed: Boolean = true,
                     expectedBalance: Long? = null
                 ) {
                     val allocations = output.wallets[input.project - 1]
                     val deposit = when (input.project) {
                         1 -> input.depositcredits/2
                         2 -> input.depositcredits/5
                         3 -> input.depositcredits/10
                         else -> {
                             throw IllegalArgumentException(
                                 "Wrong argument for projectpicker. Should be 1-3 but ${input.project} was given"
                             )
                         }
                     }
                     //All success
                     if (allSucceed) {
                         assertFalse(output.successes.contains(false))
                     } else {
                         assertTrue { output.successes.contains(false) }
                     }
                     val expect = expectedBalance ?: (deposit - (input.numberOfCalls * sampleCompute.pricePerUnit * input.units))

                     assertEquals(
                         expect,
                         allocations.first().balance
                     )

                     println(timings)
                 }

                case("10 charges valid root") {
                    input(
                        In(
                            numberOfCalls = 10,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input)
                    }
                }

                case("1000 charges valid root") {
                    input(
                        In(
                            numberOfCalls = 1000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input)
                    }
                }

                case("10000 charges valid root") {
                    input(
                        In(
                            numberOfCalls = 10000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input, false, 0)
                    }
                }

                case("100000 charges valid root") {
                    input(
                        In(
                            numberOfCalls = 100000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input, false, 0)
                    }
                }

            }
        }
//testFilter = {title, subtitle -> title == "Stress test charge, parallel small charges"  }
        run {
            class In(
                val numberOfCalls: Int,
                val project: Int,
                val productRef: ProductReference,
                val units: Long = 10,
                val periods: Long = 1,
                val depositcredits: Long = 10_000_000_000,
                val test: String = "Unknown"
            )

            class Out(
                val successes: List<Boolean>,
                val wallets: List<List<WalletAllocation>>
            )

            test<In, Out>("Stress test charge, parallel small charges") {
                execute {
                    createSampleProducts()
                    val productCategoryId = ProductCategoryId(input.productRef.category, input.productRef.provider)

                    val serviceClient = UCloudLauncher.serviceClient
                    val projectAllocations = prepareProjectChain(
                        input.depositcredits,
                        listOf(
                            Allocation(true, input.depositcredits / 2),
                            Allocation(true, input.depositcredits / 5),
                            Allocation(true, input.depositcredits / 10)
                        ),
                        productCategoryId,
                        serviceClient = serviceClient
                    )

                    val root = projectAllocations.first()
                    val mid = projectAllocations[1]
                    val leaf = projectAllocations.last()

                    val project = when (input.project) {
                        1 -> root
                        2 -> mid
                        3 -> leaf
                        else -> {
                            throw IllegalArgumentException(
                                "Wrong argument for projectpicker. Should be 1-3 but ${input.project} was given"
                            )
                        }
                    }

                    val bulk = generateChargeBulk(
                        size = input.numberOfCalls,
                        walletOwner = project.owner,
                        units = input.units,
                        periods = input.periods,
                        productReference = input.productRef,
                        user = project.username
                    )

                    val responses = mutableListOf<Boolean>()
                    val start = System.currentTimeMillis()
                    coroutineScope {
                        val charges = bulk.map { chargeRequests ->
                            launch(Dispatchers.IO) {
                                //println("Charing: ${chargeRequests.description}")
                                responses.addAll(
                                    Accounting.charge.call(
                                        bulkRequestOf(chargeRequests),
                                        serviceClient
                                    ).orThrow().responses
                                )

                            }
                        }
                        charges.forEach { joinAll(it) }
                    }
                    println("ALL DONE")
                    val end = System.currentTimeMillis()

                    timings.add(Pair(input.test, (end - start)))

                    val results = mutableListOf<List<WalletAllocation>>()

                    val rootAllocations = retrieveAllocationsInternal(root.owner, productCategoryId, serviceClient)
                    val midAllocations = retrieveAllocationsInternal(mid.owner, productCategoryId, serviceClient)
                    val leafAllocations = retrieveAllocationsInternal(leaf.owner, productCategoryId, serviceClient)

                    results.add(rootAllocations)
                    results.add(midAllocations)
                    results.add(leafAllocations)

                    Out(
                        successes = responses,
                        wallets = results
                    )
                }

                fun doCheck(
                    output: Out,
                    input: In,
                    allSucceed: Boolean = true,
                    expectedBalance: Long? = null
                ) {
                    val allocations = output.wallets[input.project - 1]
                    val deposit = when (input.project) {
                        1 -> input.depositcredits/2
                        2 -> input.depositcredits/5
                        3 -> input.depositcredits/10
                        else -> {
                            throw IllegalArgumentException(
                                "Wrong argument for projectpicker. Should be 1-3 but ${input.project} was given"
                            )
                        }
                    }
                    //All success
                    if (allSucceed) {
                        assertFalse(output.successes.contains(false))
                    } else {
                        assertTrue { output.successes.contains(false) }
                    }
                    val expect = expectedBalance ?: (deposit - (input.numberOfCalls * sampleCompute.pricePerUnit * input.units))

                    assertEquals(
                        expect,
                        allocations.first().balance
                    )

                    println(timings)
                }

                case("10 charges valid root para") {
                    input(
                        In(
                            numberOfCalls = 10,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input)
                    }
                }

                case("1000 charges valid root para") {
                    input(
                        In(
                            numberOfCalls = 1000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input)
                    }
                }

                /*case("5000 charges valid root para") {
                    input(
                        In(
                            numberOfCalls = 5000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input)
                    }
                }

                case("100000 charges valid root") {
                    input(
                        In(
                            numberOfCalls = 100000,
                            project = 1,
                            productRef = sampleCompute.toReference(),
                            units = 10L,
                            test = this.subtitle
                        )
                    )

                    check {
                        doCheck(output, input, false, 0)
                    }
                }*/
            }
        }
    }
}
