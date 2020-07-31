package dk.sdu.cloud.grant.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

interface Gift {
    val title: String
    val description: String
    val resources: List<ResourceRequest>
    val resourcesOwnedBy: String
}

data class GiftWithId(
    val id: Long,
    override val resourcesOwnedBy: String,
    override val title: String,
    override val description: String,
    override val resources: List<ResourceRequest>
) : Gift

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

data class ClaimGiftRequest(val giftId: Long)
typealias ClaimGiftResponse = Unit

typealias CreateGiftRequest = GiftWithCriteria
typealias CreateGiftResponse = FindByLongId

data class DeleteGiftRequest(val giftId: Long)
typealias DeleteGiftResponse = Unit

typealias AvailableGiftsRequest = Unit
data class AvailableGiftsResponse(val gifts: List<GiftWithId>)

typealias ListGiftsRequest = Unit
data class ListGiftsResponse(val gifts: List<GiftWithCriteria>)

object Gifts : CallDescriptionContainer("gifts") {
    const val baseContext = "/api/gifts"

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
    }

    val listGifts = call<ListGiftsRequest, ListGiftsResponse, CommonErrorMessage>("listGifts") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }
        }
    }
}