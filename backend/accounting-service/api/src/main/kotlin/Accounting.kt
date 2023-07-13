package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Accounting.documentation
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Time
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

@Serializable
data class WalletStatus(
    val owner: WalletOwner,
    val isLocked: Boolean
)

@Serializable
data class DeltaReportItem(
    val owner: WalletOwner,
    val categoryIdV2: ProductCategoryIdV2,
    val usage: Long,
    val description: ChargeDescription
)

typealias TotalUsageReportItem = DeltaReportItem

@Serializable
data class ChargeDescription(
    val description: String,
    val itemized: List<ItemizedCharge>
)

@Serializable
data class ItemizedCharge(
    val description: String,
    val usage: Long? = null,
    val productId: String? = null
)

@Serializable
data class SubAllocationRequestItem(
    val parentAllocation: String,
    val owner: WalletOwner,
    val quota: Long,    //inital balance
    val start: Long,
    val end: Long?,

    val dry: Boolean = false,

    val grantedIn: Long? = null,
    val deicAllocationId: String? = null
)

typealias SubAllocationResponse = FindByStringId?

@Serializable
data class RootAllocationRequestItem(
    val owner: WalletOwner,
    val productCategory: ProductCategoryIdV2,
    val quota: Long,
    val start: Long,
    val end: Long,

    val deicAllocationId: String? = null,

    val forcedSync:Boolean = false
)

typealias RootAllocationResponse = FindByStringId?

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class UpdateAllocationV2RequestItem(
    val allocationId: String,

    val newQuota: Long?,
    var newStart: Long?,
    val newEnd: Long?,

    val reason: String
)

typealias UpdateAllocationV2Response = Unit

@Serializable
data class FindRelevantProvidersRequestItem(
    val username: String,
    val project: String? = null,
    val useProject: Boolean
)
@Serializable
data class FindRelevantProvidersResponse(
    val providers: List<String>
)

@UCloudApiInternal(InternalLevel.BETA)
object AccountingV2 : CallDescriptionContainer("accountingv2") {
    const val baseContext = "/api/accounting"

    val check = call("check", BulkRequest.serializer(DeltaReportItem.serializer()), BulkResponse.serializer(Boolean.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "check", roles = Roles.SERVICE)
    }

    val reportDelta = call("reportDelta", BulkRequest.serializer(DeltaReportItem.serializer()), BulkResponse.serializer(Boolean.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "reportDelta", roles = Roles.PRIVILEGED)
    }

    val reportTotalUsage = call("reportTotalUsage", BulkRequest.serializer(TotalUsageReportItem.serializer()), BulkResponse.serializer(Boolean.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "reportTotalUsage")
    }

    val subAllocate = call("subAllocate", BulkRequest.serializer(SubAllocationRequestItem.serializer()), BulkResponse.serializer(SubAllocationResponse.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "subAllocate")
    }

    val rootAllocate = call("rootAllocate", BulkRequest.serializer(RootAllocationRequestItem.serializer()), BulkResponse.serializer(RootAllocationResponse.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "rootAllocate")
    }

    val updateAllocation = call("updateAllocation", BulkRequest.serializer(UpdateAllocationV2RequestItem.serializer()), UpdateAllocationV2Response.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "allocation")
    }

    val findRelevantProviders = call("findRelevantProviders", BulkRequest.serializer(FindRelevantProvidersRequestItem.serializer()), BulkResponse.serializer(FindRelevantProvidersResponse.serializer()), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "findRelevantProviders", Roles.PRIVILEGED)
    }
}
