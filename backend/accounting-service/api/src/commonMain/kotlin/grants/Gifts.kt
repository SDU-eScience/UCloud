package dk.sdu.cloud.grant.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * @see Gifts
 */
interface Gift {
    /**
     * The title of a gift
     *
     * Suitable for presentation in UIs
     */
    val title: String

    /**
     * The title of a gift
     *
     * Suitable for presentation in UIs
     */
    val description: String

    /**
     * A list of resources which will be granted to users [Gifts.claimGift] this [Gift].
     */
    val resources: List<ResourceRequest>

    /**
     * A reference to the project which owns these resources
     */
    val resourcesOwnedBy: String
}

/**
 * A [Gift] along with the [criteria] for which that can [Gifts.claimGift] this
 *
 * @see Gift
 * @see Gifts
 */
@Serializable
data class GiftWithCriteria(
    val id: Long,
    override val resourcesOwnedBy: String,
    override val title: String,
    override val description: String,
    override val resources: List<ResourceRequest>,
    val criteria: List<UserCriteria>
) : Gift {
    init {
        if (resources.isEmpty()) throw RPCException("resources cannot be empty", HttpStatusCode.BadRequest)
        if (criteria.isEmpty()) throw RPCException("resources cannot be empty", HttpStatusCode.BadRequest)
    }
}

@Serializable
data class ClaimGiftRequest(val giftId: Long)
typealias ClaimGiftResponse = Unit

typealias CreateGiftRequest = GiftWithCriteria
typealias CreateGiftResponse = FindByLongId

@Serializable
data class DeleteGiftRequest(val giftId: Long)
typealias DeleteGiftResponse = Unit

typealias AvailableGiftsRequest = Unit
@Serializable
data class AvailableGiftsResponse(val gifts: List<FindByLongId>)

object Gifts : CallDescriptionContainer("gifts") {
    const val baseContext = "/api/gifts"

    init {
        description = """
Gifts provide the system away to grant new and existing users (personal projects) credits from a project

The gifting system is primarily intended to provide credits to new users when they join the system. A 
$TYPE_REF Gift follows the same rules as $TYPE_REF dk.sdu.cloud.accounting.api.Wallets do. This means that in order to 
give a gift to someone you must transfer the resources from a project. This means that the credits will be subtracted
directly from the source project.

A $TYPE_REF Gift can only be claimed once by every user and it will be applied directly to the user's personal project.
Clients used by the end-user should use `Gifts.availableGifts` to figure out which $TYPE_REF Gift s are unclaimed by
this user. It may then claim the individual $TYPE_REF Gift s with `Gifts.claimGift`.

Administrators of a project can manage $TYPE_REF Gift s through the `Gifts.createGift`, `Gifts.deleteGift` and
`Gifts.listGifts` endpoints.           

${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val claimGift = call<ClaimGiftRequest, ClaimGiftResponse, CommonErrorMessage>("claimGift") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"claim"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Claims a [Gift] to the calling user's personal project"
            description = """
                 User errors:
                  - Users who are not eligible for claiming this [Gift] will receive an appropriate error code.
                  - If the gifting project has run out of resources then this endpoint will fail. The gift will not be 
                    marked as claimed.
            """.trimIndent()
        }
    }

    val availableGifts = call<AvailableGiftsRequest, AvailableGiftsResponse, CommonErrorMessage>("availableGifts") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"available"
            }
        }

        documentation {
            summary = "Finds a list of a user's unclaimed [Gift]s"
        }
    }

    val createGift = call<CreateGiftRequest, CreateGiftResponse, CommonErrorMessage>("createGift") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Creates a new Gift in the system"
            description = "Only project administrators can create new $TYPE_REF Gift s in the system."
        }
    }

    val deleteGift = call<DeleteGiftRequest, DeleteGiftResponse, CommonErrorMessage>("deleteGift") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Deletes a Gift by its DeleteGiftRequest.giftId"
            description = "Only project administrators of `Gift.resourcesOwnedBy` can delete the $TYPE_REF Gift ."
        }
    }
}
