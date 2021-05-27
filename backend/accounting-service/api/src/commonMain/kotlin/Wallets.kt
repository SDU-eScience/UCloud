package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
enum class WalletOwnerType {
    USER,
    PROJECT
}

@Serializable
enum class TransactionType {
    GIFTED,
    TRANSFERRED_TO_PERSONAL,
    TRANSFERRED_TO_PROJECT,
    PAYMENT
}

fun transactionComment(amount: Long, receiverId: String, transactionType: TransactionType) : String {
    val dkk = amount / 1000000
    return when (transactionType) {
        TransactionType.GIFTED -> {
            "Gifted $dkk DKK to $receiverId"
        }
        TransactionType.PAYMENT -> {
            "Payed $dkk DKK for $receiverId "
        }
        TransactionType.TRANSFERRED_TO_PERSONAL -> {
            "Transferred $dkk DKK to personal project: $receiverId"
        }
        TransactionType.TRANSFERRED_TO_PROJECT -> {
            "Transferred $dkk DKK to project: $receiverId"
        }
    }
}

@Serializable
data class RetrieveBalanceRequest(
    val id: String? = null,
    val type: WalletOwnerType? = null,
    val includeChildren: Boolean? = null,
    val showHidden: Boolean? = true
) {
    init {
        if (id != null || type != null) {
            if (id == null || type == null) {
                throw RPCException("Must specify no parameters or all parameters!", HttpStatusCode.BadRequest)
            }
        }
    }
}

@Serializable
data class WalletBalance(
    val wallet: Wallet,
    val balance: Long,
    val allocated: Long,
    val used: Long,
    val area: ProductArea
)

@Serializable
data class RetrieveBalanceResponse(
    val wallets: List<WalletBalance>
)

@Serializable
data class Wallet(
    val id: String,
    val type: WalletOwnerType,
    val paysFor: ProductCategoryId
)

@Serializable
data class AddToBalanceRequest(
    val wallet: Wallet,
    val credits: Long
) {
    init {
        if (credits < 0) throw RPCException("credits must be non-negative", HttpStatusCode.BadRequest)
    }
}

typealias AddToBalanceResponse = Unit

@Serializable
data class AddToBalanceBulkRequest(
    val requests: List<AddToBalanceRequest>
)

typealias AddToBalanceBulkResponse = Unit

@Serializable
data class SetBalanceRequest(
    val wallet: Wallet,
    val lastKnownBalance: Long,
    val newBalance: Long
)

typealias SetBalanceResponse = Unit

@Serializable
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
    val chargeImmediately: Boolean = false,

    /**
     * Ignore any errors if an entry with this [jobId] already exists
     */
    val skipIfExists: Boolean = false,

    /**
     * `true` if we should skip the limit check otherwise `false` (default) if limit checking should be active
     */
    val skipLimitCheck: Boolean = false,

    /**
     * A comment stating what the transaction is used in. Can be :
     *  GIFTED, (transferred as is a gift claim)
     *  TRANSFERRED_TO_PERSONAL, (credits are moved from project to a personal project)
     *  TRANSFERRED_TO_PROJECT, (usually only used in reservations in applications)
     *  PAYMENT (Credits are used as payment for services)
     */
    val transactionType: TransactionType
) {
    init {
        if (amount < 0) throw RPCException("Amount must be non-negative", HttpStatusCode.BadRequest)
        if (discardAfterLimitCheck && chargeImmediately) {
            throw RPCException("Cannot discard and charge at the same time", HttpStatusCode.BadRequest)
        }
    }
}

typealias ReserveCreditsResponse = Unit

@Serializable
data class ReserveCreditsBulkRequest(
    val reservations: List<ReserveCreditsRequest>
)

typealias ReserveCreditsBulkResponse = Unit

@Serializable
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

@Serializable
data class TransferToPersonalRequest(val transfers: List<SingleTransferRequest>)
@Serializable
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

@Serializable
data class RetrieveWalletsForProjectsRequest(
    val projectIds: List<String>
)

typealias RetrieveWalletsForProjectsResponse = List<Wallet>

@Serializable
data class WalletsGrantProviderCreditsRequest(val provider: String)
typealias WalletsGrantProviderCreditsResponse = Unit

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
                +boundTo(RetrieveBalanceRequest::showHidden)
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

    val reserveCreditsBulk = call<ReserveCreditsBulkRequest, ReserveCreditsBulkResponse, CommonErrorMessage>("reserveCreditsBulk") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"reserve-credits-bulk"
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

    val retrieveWalletsFromProjects =
        call<RetrieveWalletsForProjectsRequest,
            RetrieveWalletsForProjectsResponse,
            CommonErrorMessage>("retrieveWalletsFromProjects")
        {
            auth {
                access = AccessRight.READ
                roles = Roles.PRIVILEGED
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"retrieveWallets"
                }

                body { bindEntireRequestFromBody()}
            }
        }

    val grantProviderCredits = call<WalletsGrantProviderCreditsRequest, WalletsGrantProviderCreditsResponse,
        CommonErrorMessage>("grantProviderCredits") {
        httpUpdate(baseContext, "grantProviderCredits", roles = Roles.PRIVILEGED)
    }
}
