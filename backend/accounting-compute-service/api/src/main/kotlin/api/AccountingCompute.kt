package dk.sdu.cloud.accounting.compute.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.compute.MachineType
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

data class ComputeOverviewRequest(
    val project: String?,
    val group: String?
) {
    init {
        if (project == null && group != null) {
            throw RPCException("Group supplied without a project", HttpStatusCode.BadRequest)
        }
    }
}

data class OverviewByType(
    val type: MachineType,
    val creditsUsed: Long,
    val creditsRemaining: Long
)

data class ComputeOverviewResponse(val overview: List<OverviewByType>)

data class ComputeChartRequest(
    val project: String?,
    val group: String?,
    val pStart: Long = -1L,
    val pEnd: Long = -1L
) {
    init {
        if (pStart < -1L || pEnd < -1L) throw RPCException("Invalid period", HttpStatusCode.BadRequest)
        if (pEnd < pStart) throw RPCException("Invalid period", HttpStatusCode.BadRequest)

        if (project == null && group != null) {
            throw RPCException("Group supplied without a project", HttpStatusCode.BadRequest)
        }
    }
}

data class ComputeChartPoint(
    val timestamp: Long,
    val creditsUsed: Long
)

data class ComputeChartResponse(
    val daily: List<ComputeChartPoint>,
    val cumulative: List<ComputeChartPoint>
)

data class CreditsAccount(
    val id: String,
    val type: AccountType,
    val machineType: MachineType
)

enum class AccountType {
    USER,
    PROJECT
}

data class RetrieveBalanceRequest(
    val id: String,
    val type: AccountType
)

data class ComputeBalance(
    val type: MachineType,
    val creditsRemaining: Long
)

data class RetrieveBalanceResponse(
    val balance: List<ComputeBalance>
)

data class GrantCreditsRequest(
    val account: CreditsAccount,
    val credits: Long
)

typealias GrantCreditsResponse = Unit

data class SetBalanceRequest(
    val account: CreditsAccount,
    val lastKnownBalance: Long,
    val newBalance: Long
)

typealias SetBalanceResponse = Unit

data class BreakdownPoint(
    val username: String,
    val creditsUsed: Long
)
data class BreakdownRequest(val project: String)
data class BreakdownResponse(val points: List<BreakdownPoint>)

data class ReserveCreditsRequest(
    val jobId: String,
    val amount: Long,
    val expiresAt: Long,
    val account: CreditsAccount,
    val jobInitiatedBy: String
)

typealias ReserveCreditsResponse = Unit

data class ChargeReservationRequest(val name: String, val amount: Long)
typealias ChargeReservationResponse = Unit

object AccountingCompute : CallDescriptionContainer("accounting.compute") {
    const val baseContext = "/api/accounting/compute"

    val computeOverview = call<ComputeOverviewRequest, ComputeOverviewResponse, CommonErrorMessage>("computeOverview") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ComputeOverviewRequest::project)
                +boundTo(ComputeOverviewRequest::group)
            }
        }
    }

    val computeChart = call<ComputeChartRequest, ComputeChartResponse, CommonErrorMessage>("computeChart") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"chart"
            }

            params {
                +boundTo(ComputeChartRequest::project)
                +boundTo(ComputeChartRequest::group)
                +boundTo(ComputeChartRequest::pStart)
                +boundTo(ComputeChartRequest::pEnd)
            }
        }
    }

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
            }
        }
    }

    val breakdown = call<BreakdownRequest, BreakdownResponse, CommonErrorMessage>("breakdown") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"breakdown"
            }

            params {
                +boundTo(BreakdownRequest::project)
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
