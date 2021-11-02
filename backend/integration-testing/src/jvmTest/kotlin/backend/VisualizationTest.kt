package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Accounting
import dk.sdu.cloud.accounting.api.ChargeWalletRequestItem
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.db
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.*

class VisualizationTest : IntegrationTest() {
    override fun defineTests() {
        run {
            data class Charge(val idx: Int, val charge: Long)
            data class ChargeRound(val timestamp: Long, val charges: List<Charge>)
            class In(
                val start: Long,
                val end: Long,
                val allocations: List<Allocation>,
                val charges: List<ChargeRound>,
                val products: List<Product>,
                val breadth: Int = 1,
            )

            class Out

            test<In, Out>("Usage test") {
                execute {
                    val categories = input.products.map { it.category }.toSet().toList()
                    val leaves = prepareProjectChain(
                        1_000_000.DKK, input.allocations, categories.first(),
                        breadth = input.breadth,
                        moreProducts = categories.drop(1)
                    )

                    for (round in input.charges) {
                        if (round.charges.isEmpty()) continue

                        for (product in input.products) {
                            Accounting.charge.call(
                                BulkRequest(round.charges.map {
                                    ChargeWalletRequestItem(
                                        leaves[it.idx].owner,
                                        it.charge,
                                        1L,
                                        product.toReference(),
                                        leaves[it.idx].username,
                                        "Charge",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                }),
                                serviceClient
                            ).orThrow()

                            // NOTE(Dan): It is really hard to mock the time-source of postgresql in a reliable way. It is
                            // much easier to just manually back-date transactions with a query. The query below assumes
                            // that we can easily distinguish between timestamps from the input and anything we create. We
                            // do this by simply looking at recent transactions. As a result, test input should not use
                            // recent timestamps.
                            db.withSession { session ->
                                session.sendPreparedStatement(
                                    {
                                        setParameter("timestamp", round.timestamp)
                                    },
                                    """
                                        update accounting.transactions
                                        set created_at = to_timestamp(:timestamp / 1000.0)
                                        where now() - created_at < '1 minute'::interval
                                    """
                                )
                            }
                        }
                    }

                    Out()
                }

                case("Test data for development") {
                    val baseTimestamp = 100_000
                    input(In(
                        0L, 0L,
                        // 2 allocations to make sure we get the correct data
                        listOf(
                            Allocation(true, 1_000_000.DKK),
                            Allocation(true, 1_000_000.DKK)
                        ),
                        // The first allocation has much higher usage, but otherwise mirrors the other one
                        listOf(
                            ChargeRound(
                                baseTimestamp + 0L,
                                listOf(
                                    Charge(0, 10000),
                                    Charge(1, 100),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 1L,
                                listOf(
                                    Charge(0, 10000),
                                    Charge(1, 100),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 2L,
                                listOf(
                                    Charge(0, 20000),
                                    Charge(1, 200),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 3L,
                                listOf(
                                    Charge(0, 30000),
                                    Charge(1, 300),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 4L,
                                listOf(
                                    Charge(0, 10000),
                                    Charge(1, 100),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 5L,
                                listOf(
                                    Charge(0, 20000),
                                    Charge(1, 200),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 6L,
                                emptyList()
                            ),
                            ChargeRound(
                                baseTimestamp + 7L,
                                listOf(
                                    Charge(0, 30000),
                                    Charge(1, 300),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 8L,
                                listOf(
                                    Charge(0, 10000),
                                    Charge(1, 100),
                                )
                            ),
                            ChargeRound(
                                baseTimestamp + 9L,
                                listOf(
                                    Charge(0, 20000),
                                    Charge(1, 200),
                                )
                            ),
                        ),
                        listOf(sampleCompute, sampleCompute2, sampleCompute3, sampleCompute4, sampleCompute5,
                            sampleNetworkIp, sampleStorageDifferential)
                    ))
                    check {}
                }
            }
        }
    }
}
