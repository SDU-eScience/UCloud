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
    val includeChildren: Boolean? = null
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

data class AddToBalanceRequest(
    val wallet: Wallet,
    val credits: Long
) {
    init {
        if (credits < 0) throw RPCException("credits must be non-negative", HttpStatusCode.BadRequest)
    }
}

typealias AddToBalanceResponse = Unit

data class AddToBalanceBulkRequest(
    val requests: List<AddToBalanceRequest>
)

typealias AddToBalanceBulkResponse = Unit

data class SetBalanceRequest(
    val wallet: Wallet,
    val lastKnownBalance: Long,
    val newBalance: Long
)

typealias SetBalanceResponse = Unit

data class SetNotificationSentRequest(
    val wallet: Wallet,
    val sent: Boolean
)

typealias SetNotificationSentResponse = Unit

data class ReserveCreditsRequest(
    val jobId: String,
    val amount: Long,
    val expiresAt: Long,
    val account: Wallet,
    val jobInitiatedBy: String,
    val productId: String,
    val productUnits: Long,

    /**
     * If this is true the reservation will be deleted immediately after the limit check has passed
     *
     * The reservation will never be committed. This allows clients to perform a limit check without actually
     * committing anything.
     */
    val discardAfterLimitCheck: Boolean = false,

    /**
     * Immediately charge the wallet for the [amount] specified.
     */
    val chargeImmediately: Boolean = false
) {
    init {
        if (amount < 0) throw RPCException("Amount must be non-negative", HttpStatusCode.BadRequest)
        if (discardAfterLimitCheck && chargeImmediately) {
            throw RPCException("Cannot discard and charge at the same time", HttpStatusCode.BadRequest)
        }
    }
}

typealias ReserveCreditsResponse = Unit

data class ChargeReservationRequest(
    val name: String,
    val amount: Long,
    val productUnits: Long
) {
    init {
        if (amount < 0) throw RPCException("Amount must be non-negative", HttpStatusCode.BadRequest)
    }
}

typealias ChargeReservationResponse = Unit

data class TransferToPersonalRequest(val transfers: List<SingleTransferRequest>)
data class SingleTransferRequest(
    val initiatedBy: String,
    val amount: Long,
    val sourceAccount: Wallet,
    val destinationAccount: Wallet
) {
    init {
        if (amount < 0) throw RPCException("Amount must be non-negative", HttpStatusCode.BadRequest)

        if (destinationAccount.type != WalletOwnerType.USER) {
            throw RPCException("Destination account must be a personal project!", HttpStatusCode.BadRequest)
        }

        if (sourceAccount.paysFor != destinationAccount.paysFor) {
            throw RPCException("Both source and destination must target same wallet", HttpStatusCode.BadRequest)
        }
    }
}

typealias TransferToPersonalResponse = Unit

object Wallets : CallDescriptionContainer("wallets") {
    const val baseContext = "/api/accounting/wallets"

    val retrieveBalance = call<RetrieveBalanceRequest, RetrieveBalanceResponse, CommonErrorMessage>("retrieveBalance") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
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

    val addToBalance = call<AddToBalanceRequest, AddToBalanceResponse, CommonErrorMessage>("addToBalance") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
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

    val addToBalanceBulk = call<AddToBalanceBulkRequest, AddToBalanceBulkResponse, CommonErrorMessage>("addToBalanceBulk") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"add-credits-bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val setBalance = call<SetBalanceRequest, SetBalanceResponse, CommonErrorMessage>("setBalance") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
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
            roles = Roles.PRIVILEGED
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
            roles = Roles.PRIVILEGED
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

    val transferToPersonal = call<TransferToPersonalRequest, TransferToPersonalResponse, CommonErrorMessage>(
        "transferToPersonal"
    ) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"transfer"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
