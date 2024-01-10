package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.utils.*
import java.util.*

class GiftTest : IntegrationTest() {

    override fun defineTests() {
        run {
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
                    var giftId = 0L
                    val normalUser = createUser(
                        "user-${UUID.randomUUID()}",
                        email = input.userEmail,
                        organization = input.userOrganization
                    )
                    val evilUser = createUser("evil-${UUID.randomUUID()}")
                    createSampleProducts()
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val createdProject = initializeNormalProject(root)
                    for (simplifiedGift in input.gifts) {
                        val gift = GiftWithCriteria(
                            id = 0L,
                            resourcesOwnedBy = createdProject.projectId,
                            title = "My gift ${UUID.randomUUID()}",
                            description = "Description",
                            resources = listOf(
                                GrantApplication.AllocationRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    createdProject.projectId,
                                    1000,
                                    null,
                                    GrantApplication.Period(
                                        null,
                                        null
                                    )
                                )
                            ),
                            criteria = simplifiedGift.criteria,
                            renewEvery = 0
                        )

                        Gifts.createGift.call(gift, evilUser.client).assertUserError()
                        giftId = Gifts.createGift.call(gift, createdProject.piClient).orThrow().id
                    }

                    val availableGifts = Gifts.availableGifts.call(AvailableGiftsRequest, normalUser.client).orThrow()
                    for (gift in availableGifts.gifts) {
                        Gifts.claimGift.call(ClaimGiftRequest(gift.id), normalUser.client).orThrow()
                        Gifts.claimGift.call(ClaimGiftRequest(gift.id), normalUser.client).assertUserError()
                    }

                    //Deletes all data about gift before next text
                    Gifts.deleteGift.call(DeleteGiftRequest(giftId), createdProject.piClient).orThrow()

                    val walletsOfUser = Wallets.retrieveWalletsInternal.call(
                        WalletsInternalRetrieveRequest(
                            WalletOwner.User(normalUser.username)
                        ),
                        adminClient
                    ).orThrow().wallets
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
                                ?.sumOf { it.balance } == 1000L
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
                                ?.sumOf { it.balance } ?: 0L) == 0L
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
                                ?.sumOf { it.balance } ?: 0L) == 0L
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
                                ?.sumOf { it.balance } == 1000L
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
                                ?.sumOf { it.balance } == 1000L
                        }
                    }
                }
            }
        }
    }
}
