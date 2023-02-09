package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.accounting.api.projects.UserCriteria
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.assertUserError
import dk.sdu.cloud.service.test.assertThatInstance
import java.util.*

class GiftTest : IntegrationTest() {
    override fun defineTests() {
        run {
            prionl()
            data class SimplifiedGift(val criteria: List<UserCriteria>)
            class In(
                val gifts: List<SimplifiedGift>,
                val userEmail: String = "user@ucloud.dk",
                val userOrganization: String = "ucloud.dk",
            )
            class Out(
                val availableGifts: AvailableGiftsResponse,
                val walletsOfUser: List<Wallet>
            )

            test<In, Out>("Gifts, expected flow") {
                execute {
                    val grantPi = createUser("pi-${UUID.randomUUID()}")
                    val normalUser = createUser(
                        "user-${UUID.randomUUID()}",
                        email = input.userEmail,
                        organization = input.userOrganization
                    )
                    val evilUser = createUser("evil-${UUID.randomUUID()}")

                    val createdProject = initializeRootProject(grantPi.username)
                    for (simplifiedGift in input.gifts) {
                        val gift = GiftWithCriteria(
                            0L,
                            createdProject,
                            "My gift ${UUID.randomUUID()}",
                            "Description",
                            listOf(
                                GrantApplication.AllocationRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    createdProject,
                                    1000.DKK,
                                    null,
                                    GrantApplication.Period(
                                        System.currentTimeMillis()+1000,
                                        System.currentTimeMillis()+1000000
                                    )
                                )
                            ),
                            simplifiedGift.criteria
                        )

                        Gifts.createGift.call(gift, evilUser.client).assertUserError()
                        Gifts.createGift.call(gift, grantPi.client).orThrow()
                    }

                    val availableGifts = Gifts.availableGifts.call(AvailableGiftsRequest, normalUser.client).orThrow()
                    for (gift in availableGifts.gifts) {
                        Gifts.claimGift.call(ClaimGiftRequest(gift.id), normalUser.client).orThrow()
                        Gifts.claimGift.call(ClaimGiftRequest(gift.id), normalUser.client).assertUserError()
                    }

                    val walletsOfUser = Wallets.browse.call(WalletBrowseRequest(), normalUser.client).orThrow().items
                    Out(availableGifts, walletsOfUser)
                }

                case("Gifts for all") {
                    input(In(
                        listOf(
                            SimplifiedGift(
                                listOf(UserCriteria.Anyone())
                            )
                        )
                    ))

                    check {
                        assertThatInstance(output.availableGifts, "had one gift") { it.gifts.size == 1 }
                        assertThatInstance(output.walletsOfUser, "has a new allocation") {
                            it.find { it.paysFor == sampleCompute.category }?.allocations
                                ?.sumOf { it.balance } == 1000.DKK
                        }
                    }
                }

                case("Excluded by org") {
                    input(In(
                        listOf(
                            SimplifiedGift(
                                listOf(UserCriteria.WayfOrganization("ucloud.dk")),
                            )
                        ),
                        userOrganization = "sdu.dk"
                    ))

                    check {
                        assertThatInstance(output.availableGifts, "has no gifts") { it.gifts.isEmpty() }
                        assertThatInstance(output.walletsOfUser, "has a new allocation") { wallets ->
                            (wallets.find { it.paysFor == sampleCompute.category }?.allocations
                                ?.sumOf { it.balance } ?: 0L) == 0.DKK
                        }
                    }
                }

                case("Excluded by mail") {
                    input(In(
                        listOf(
                            SimplifiedGift(
                                listOf(UserCriteria.EmailDomain("ucloud.dk")),
                            )
                        ),
                        userEmail = "user@sdu.dk"
                    ))

                    check {
                        assertThatInstance(output.availableGifts, "has no gifts") { it.gifts.isEmpty() }
                        assertThatInstance(output.walletsOfUser, "has a new allocation") { wallets ->
                            (wallets.find { it.paysFor == sampleCompute.category }?.allocations
                                ?.sumOf { it.balance } ?: 0L) == 0.DKK
                        }
                    }
                }

                case("Included by mail") {
                    input(In(
                        listOf(
                            SimplifiedGift(
                                listOf(UserCriteria.EmailDomain("sdu.dk")),
                            )
                        ),
                        userEmail = "user@sdu.dk"
                    ))

                    check {
                        assertThatInstance(output.availableGifts, "had one gift") { it.gifts.size == 1 }
                        assertThatInstance(output.walletsOfUser, "has a new allocation") {
                            it.find { it.paysFor == sampleCompute.category }?.allocations
                                ?.sumOf { it.balance } == 1000.DKK
                        }
                    }
                }

                case("Included by organization") {
                    input(In(
                        listOf(
                            SimplifiedGift(
                                listOf(UserCriteria.WayfOrganization("sdu.dk")),
                            )
                        ),
                        userOrganization = "sdu.dk"
                    ))

                    check {
                        assertThatInstance(output.availableGifts, "had one gift") { it.gifts.size == 1 }
                        assertThatInstance(output.walletsOfUser, "has a new allocation") {
                            it.find { it.paysFor == sampleCompute.category }?.allocations
                                ?.sumOf { it.balance } == 1000.DKK
                        }
                    }
                }
            }
        }
    }
}
