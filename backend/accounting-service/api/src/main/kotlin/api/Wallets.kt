package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

enum class WalletOwnerType {
    USER,
    PROJECT
}

data class RetrieveBalanceRequest(
    val id: String?,
    val type: WalletOwnerType?,
    val includeChildren: Boolean?
) {
    init {
        if (id != null || type != null) {
            if (id == null || type == null) {
                throw RPCException("Must specify no parameters or all parameters!", HttpStatusCode.BadRequest)
            }
        }
    }
}

data class WalletBalance(
    val wallet: Wallet,
    val balance: Long,
    val area: ProductArea
)

data class RetrieveBalanceResponse(
    val wallets: List<WalletBalance>
)

data class Wallet(
    val id: String,
    val type: WalletOwnerType,
    val paysFor: ProductCategoryId
)

data class GrantCreditsRequest(
    val wallet: Wallet,
    val credits: Long
)

typealias GrantCreditsResponse = Unit

data class SetBalanceRequest(
    val wallet: Wallet,
    val lastKnownBalance: Long,
    val newBalance: Long
)

typealias SetBalanceResponse = Unit

data class ReserveCreditsRequest(
    val jobId: String,
    val amount: Long,
    val expiresAt: Long,
    val account: Wallet,
    val jobInitiatedBy: String,
    val productId: String,
    val productUnits: Long
)

typealias ReserveCreditsResponse = Unit

data class ChargeReservationRequest(val name: String, val amount: Long, val productUnits: Long)
typealias ChargeReservationResponse = Unit

object Wallets : CallDescriptionContainer("wallets") {
    const val baseContext = "/api/accounting/wallets"

    val retrieveBalance = call<RetrieveBalanceRequest, RetrieveBalanceResponse, CommonErrorMessage>("retrieveBalance") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"balance"
            }

            params {
                +boundTo(RetrieveBalanceRequest::id)
                +boundTo(RetrieveBalanceRequest::type)
                +boundTo(RetrieveBalanceRequest::includeChildren)
            }
        }
    }

    val grantCredits = call<GrantCreditsRequest, GrantCreditsResponse, CommonErrorMessage>("grantCredits") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"add-credits"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val setBalance = call<SetBalanceRequest, SetBalanceResponse, CommonErrorMessage>("setBalance") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"set-balance"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val reserveCredits = call<ReserveCreditsRequest, ReserveCreditsResponse, CommonErrorMessage>("reserveCredits") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"reserve-credits"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val chargeReservation = call<ChargeReservationRequest, ChargeReservationResponse, CommonErrorMessage>(
        "chargeReservation"
    ) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"charge-reservation"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
